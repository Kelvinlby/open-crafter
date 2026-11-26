package mod.kelvinlby.opencrafter.client.handview;

import mod.kelvinlby.opencrafter.mixin.handview.CameraAccessor;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * The first-person hand pass's coordinate frame: camera space (x right, y up, -z forward),
 * drawn with the HUD field of view instead of the world's.
 */
final class HandSpace {
	private HandSpace() {
	}

	/** World axes to camera axes: the view's rotation. */
	static Matrix4f viewRotation() {
		Camera camera = Minecraft.getInstance().gameRenderer.mainCamera();
		return new Matrix4f().rotation(camera.rotation().conjugate(new Quaternionf()));
	}

	/** Camera space as the world draws it to camera space as the hand pass draws it (another field of view). */
	static Matrix4f fovCorrection() {
		Camera camera = Minecraft.getInstance().gameRenderer.mainCamera();
		float ratio = tanHalf(((CameraAccessor) camera).openCrafter$hudFov()) / tanHalf(camera.getFov());
		return new Matrix4f().scaling(ratio, ratio, 1);
	}

	/** A block's corner relative to the eye, in world axes. */
	static Vector3f offsetFromEye(BlockPos pos) {
		Vec3 eye = Minecraft.getInstance().gameRenderer.mainCamera().position();
		return new Vector3f((float) (pos.getX() - eye.x), (float) (pos.getY() - eye.y), (float) (pos.getZ() - eye.z));
	}

	private static float tanHalf(float fovDegrees) {
		return (float) Math.tan(Math.toRadians(fovDegrees) / 2);
	}
}
