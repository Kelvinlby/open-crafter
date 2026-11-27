package mod.kelvinlby.opencrafter.client.handview;

import mod.kelvinlby.opencrafter.mixin.handview.CameraAccessor;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * The first-person hand pass's coordinate frame: camera space (x right, y up, -z forward),
 * drawn with the HUD field of view instead of the world's.
 */
final class HandSpace {
	/** Where vanilla holds an item ({@code applyItemArmTransform}, item fully raised), right arm. */
	private static final float REST_X = 0.56F;
	private static final float REST_Y = -0.52F;
	private static final float REST_Z = -0.72F;
	/** Closer than this (blocks) in front of the eye counts as off screen. */
	private static final float ON_SCREEN_NEAR = 0.05F;
	/** How far past the screen edge a model's centre may go and still count as in view. */
	private static final float ON_SCREEN_MARGIN = 1.15F;

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

	/** Whether a point in camera space (as the world draws it) is in view, with a small margin for the model's size. */
	static boolean onScreen(Vector3f point) {
		if (point.z > -ON_SCREEN_NEAR) {
			return false;
		}
		Minecraft minecraft = Minecraft.getInstance();
		float tanY = tanHalf(minecraft.gameRenderer.mainCamera().getFov()) * ON_SCREEN_MARGIN;
		float aspect = (float) minecraft.getWindow().getWidth() / Math.max(1, minecraft.getWindow().getHeight());
		float depth = -point.z;
		return Math.abs(point.y) <= depth * tanY && Math.abs(point.x) <= depth * tanY * aspect;
	}

	/** Where vanilla holds an item by {@code arm}, in the hand pass's camera space (before view bobbing). */
	static Vector3f itemRest(HumanoidArm arm) {
		float side = arm == HumanoidArm.RIGHT ? 1 : -1;
		return new Vector3f(side * REST_X, REST_Y, REST_Z);
	}

	/** A world point in the hand pass's camera space: where it shows on screen, at its distance from the eye. */
	static Vector3f inHandSpace(Vec3 point) {
		Vec3 eye = Minecraft.getInstance().gameRenderer.mainCamera().position();
		Vector3f offset = new Vector3f((float) (point.x - eye.x), (float) (point.y - eye.y), (float) (point.z - eye.z));
		return fovCorrection().mul(viewRotation()).transformPosition(offset);
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
