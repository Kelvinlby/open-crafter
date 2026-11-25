package mod.kelvinlby.opencrafter.client.vision.flow;

import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector4f;

/**
 * The camera of one captured frame exactly as the renderer used it: world projection (with
 * view bobbing and screen effects baked in), view rotation, camera position and the separate
 * first-person hand projection.
 */
public final class CameraSnapshot {
	public final Matrix4f worldProjection = new Matrix4f();
	public final Matrix4f viewRotation = new Matrix4f();
	public final Matrix4f handProjection = new Matrix4f();
	public double x;
	public double y;
	public double z;
	public boolean levelRendered;

	public void reset() {
		worldProjection.identity();
		viewRotation.identity();
		handProjection.identity();
		x = 0;
		y = 0;
		z = 0;
		levelRendered = false;
	}

	/** Depth-buffer value of "nothing rendered" (0 with Minecraft's reversed Z, 1 for a standard projection). */
	public float clearDepth(boolean zeroToOne) {
		return farDepth(worldProjection, zeroToOne) < 0.5F ? 0.0F : 1.0F;
	}

	static float farDepth(Matrix4fc projection, boolean zeroToOne) {
		if (!levelProjectionIsValid(projection)) {
			return 0.0F;
		}
		Vector4f clip = projection.transform(new Vector4f(0.0F, 0.0F, -1.0e5F, 1.0F));
		float ndcZ = clip.z / clip.w;
		return zeroToOne ? ndcZ : ndcZ * 0.5F + 0.5F;
	}

	private static boolean levelProjectionIsValid(Matrix4fc projection) {
		return (projection.properties() & Matrix4fc.PROPERTY_IDENTITY) == 0 && projection.m23() != 0.0F;
	}
}
