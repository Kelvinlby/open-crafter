package mod.kelvinlby.opencrafter.client.interaction.sensor;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector4f;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Projects a world point into the frame being captured, in framebuffer pixels (+x right, +y down).
 * Uses the camera's projection without view bobbing, so it can be off by a few pixels while walking.
 */
final class ScreenProjection {
	private ScreenProjection() {
	}

	/** {@code [x, y]} in pixels, or null when the point is behind the camera or the level is not rendered. */
	static @Nullable List<Double> pixels(Vec3 world, int width, int height) {
		Minecraft minecraft = Minecraft.getInstance();
		if (!minecraft.gameRenderer.gameRenderState().shouldRenderLevel) {
			return null;
		}
		CameraRenderState camera = minecraft.gameRenderer.gameRenderState().levelRenderState.cameraRenderState;
		Vec3 relative = world.subtract(camera.pos);
		Vector4f clip = new Vector4f((float) relative.x, (float) relative.y, (float) relative.z, 1.0F);
		camera.viewRotationMatrix.transform(clip);
		camera.projectionMatrix.transform(clip);
		if (clip.w <= 0.0F) {
			return null;
		}
		double ndcX = clip.x / clip.w;
		double ndcY = clip.y / clip.w;
		return List.of((ndcX + 1.0) * 0.5 * width, (1.0 - ndcY) * 0.5 * height);
	}
}
