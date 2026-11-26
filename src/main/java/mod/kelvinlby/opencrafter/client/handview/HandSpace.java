package mod.kelvinlby.opencrafter.client.handview;

import mod.kelvinlby.opencrafter.mixin.handview.CameraAccessor;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * The first-person hand pass's coordinate frame: camera space (x right, y up, -z forward),
 * drawn with the HUD field of view instead of the world's.
 */
final class HandSpace {
	/** Where vanilla holds an item, relative to the camera ({@code applyItemArmTransform}). */
	private static final float ITEM_X = 0.56F;
	private static final float ITEM_Y = -0.52F;
	private static final float ITEM_Z = -0.72F;

	private HandSpace() {
	}

	/** A world position, placed so that the hand pass draws it where the world pass does. */
	static Vector3f fromWorld(Vec3 world) {
		Camera camera = Minecraft.getInstance().gameRenderer.mainCamera();
		Vec3 eye = camera.position();
		Vector3f local = new Vector3f((float) (world.x - eye.x), (float) (world.y - eye.y), (float) (world.z - eye.z));
		local.rotate(camera.rotation().conjugate(new Quaternionf()));
		float fovRatio = tanHalf(((CameraAccessor) camera).openCrafter$hudFov()) / tanHalf(camera.getFov());
		local.x *= fovRatio;
		local.y *= fovRatio;
		return local;
	}

	static Vector3f itemRest(HumanoidArm arm) {
		return new Vector3f(arm == HumanoidArm.RIGHT ? ITEM_X : -ITEM_X, ITEM_Y, ITEM_Z);
	}

	private static float tanHalf(float fovDegrees) {
		return (float) Math.tan(Math.toRadians(fovDegrees) / 2);
	}
}
