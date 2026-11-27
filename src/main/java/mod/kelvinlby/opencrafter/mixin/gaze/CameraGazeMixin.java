package mod.kelvinlby.opencrafter.mixin.gaze;

import mod.kelvinlby.opencrafter.client.gaze.GazeHooks;
import mod.kelvinlby.opencrafter.client.interaction.event.Aim;
import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Turns the rendered camera right after vanilla aligned it with the camera entity, before the cull
 * frustum and the render state are taken from it: everything drawn (and the vision sensor's flow)
 * uses the turned camera, while the player's own rotation stays untouched.
 */
@Mixin(Camera.class)
abstract class CameraGazeMixin {
	@Shadow
	protected abstract void setRotation(float yRot, float xRot);

	@Inject(method = "alignWithEntity", at = @At("TAIL"))
	private void openCrafter$gaze(float partialTicks, CallbackInfo ci) {
		Aim aim = GazeHooks.cameraAim((Camera) (Object) this);
		if (aim != null) {
			setRotation(aim.yaw(), aim.pitch());
		}
	}
}
