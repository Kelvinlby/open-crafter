package mod.kelvinlby.opencrafter.client.vision.flow;

import org.joml.Matrix4d;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Checks the reprojection math of {@code vision_flow_static.fsh} (mirrored here line by line in
 * float precision, like the GPU) against analytic flow, using Minecraft's projection convention
 * (reversed Z: near and far swapped, depth in [0, 1]).
 */
class StaticReprojectionTest {
	private static final int WIDTH = 854;
	private static final int HEIGHT = 480;
	private static final float FOV_DEGREES = 70.0F;
	private static final double FOCAL = (HEIGHT / 2.0) / Math.tan(Math.toRadians(FOV_DEGREES / 2.0));

	@Test
	void clearDepthOfReversedZProjectionIsZero() {
		CameraSnapshot camera = camera(0.0F, 0.0);
		assertEquals(0.0F, camera.clearDepth(true));
		assertEquals(1.0F, standardCamera().clearDepth(true));
	}

	@Test
	void yawRotationShiftsCenterByFocalTimesTangent() {
		CameraSnapshot prev = camera(0.0F, 0.0);
		CameraSnapshot cur = camera(2.0F, 0.0);
		float[] flow = flowAtCenter(prev, cur, 20.0F);
		assertEquals(-FOCAL * Math.tan(Math.toRadians(2.0)), flow[0], 1.0e-3);
		assertEquals(0.0, flow[1], 1.0e-3);
	}

	@Test
	void sidewaysTranslationGivesParallaxInverseToDistance() {
		CameraSnapshot prev = camera(0.0F, 0.0);
		CameraSnapshot cur = camera(0.0F, 0.5);
		for (float distance : new float[]{2.0F, 10.0F, 100.0F}) {
			float[] flow = flowAtCenter(prev, cur, distance);
			assertEquals(-FOCAL * 0.5 / distance, flow[0], 2.0e-3 * Math.max(1.0, FOCAL * 0.5 / distance));
		}
	}

	/** Flow of the pixel at the screen center whose surface lies {@code distance} blocks ahead. */
	private static float[] flowAtCenter(CameraSnapshot prev, CameraSnapshot cur, float distance) {
		float pixelX = WIDTH / 2.0F + 0.5F;
		float pixelY = HEIGHT / 2.0F + 0.5F;
		Vector4f ndc = toNdc(pixelX, pixelY);
		float depth = depthOf(prev, ndc, distance);
		Matrix4f prevInvProj = inverse(prev.worldProjection);
		Vector4f view = prevInvProj.transform(new Vector4f(ndc.x, ndc.y, depth, 1.0F));
		Vector3f prevView = new Vector3f(view.x / view.w, view.y / view.w, view.z / view.w);
		Vector3f rel = inverse(prev.viewRotation).transformPosition(prevView, new Vector3f())
			.add((float) (prev.x - cur.x), (float) (prev.y - cur.y), (float) (prev.z - cur.z));
		Vector3f curView = cur.viewRotation.transformPosition(rel, new Vector3f());
		Vector4f clip = cur.worldProjection.transform(new Vector4f(curView, 1.0F));
		float px = (clip.x / clip.w * 0.5F + 0.5F) * WIDTH;
		float py = (clip.y / clip.w * 0.5F + 0.5F) * HEIGHT;
		return new float[]{px - pixelX, py - pixelY};
	}

	/** Depth-buffer value of the surface `distance` ahead along the ray through `ndc`. */
	private static float depthOf(CameraSnapshot camera, Vector4f ndc, float distance) {
		Vector4f clip = camera.worldProjection.transform(new Vector4f(ndc.x * distance, ndc.y * distance, -distance, 1.0F));
		return clip.z / clip.w;
	}

	private static Vector4f toNdc(float pixelX, float pixelY) {
		return new Vector4f(pixelX / WIDTH * 2.0F - 1.0F, pixelY / HEIGHT * 2.0F - 1.0F, 0.0F, 1.0F);
	}

	private static Matrix4f inverse(Matrix4f matrix) {
		return new Matrix4f().set(new Matrix4d(matrix).invert());
	}

	/** A camera like Minecraft's: reversed-Z perspective, yaw about +Y, positioned along X. */
	private static CameraSnapshot camera(float yawDegrees, double x) {
		CameraSnapshot camera = new CameraSnapshot();
		camera.worldProjection.setPerspective((float) Math.toRadians(FOV_DEGREES), (float) WIDTH / HEIGHT, 512.0F, 0.05F, true);
		camera.viewRotation.rotationY((float) Math.toRadians(yawDegrees));
		camera.x = x;
		camera.levelRendered = true;
		return camera;
	}

	private static CameraSnapshot standardCamera() {
		CameraSnapshot camera = new CameraSnapshot();
		camera.worldProjection.setPerspective((float) Math.toRadians(FOV_DEGREES), (float) WIDTH / HEIGHT, 0.05F, 512.0F, true);
		return camera;
	}
}
