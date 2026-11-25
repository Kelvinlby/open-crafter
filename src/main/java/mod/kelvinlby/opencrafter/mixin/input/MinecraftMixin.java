package mod.kelvinlby.opencrafter.mixin.input;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import mod.kelvinlby.opencrafter.client.input.InputHooks;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Frame hook for lease expiry, and lets a simulated attack hold keep mining when the mouse is
 * not grabbed (game window unfocused): vanilla only continues breaking a block while grabbed.
 */
@Mixin(Minecraft.class)
abstract class MinecraftMixin {
	@Inject(method = "runTick", at = @At("HEAD"))
	private void opencrafter$onFrameStart(boolean advanceGameTime, CallbackInfo ci) {
		InputHooks.onFrame();
	}

	@ModifyExpressionValue(
		method = "handleKeybinds",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/client/MouseHandler;isMouseGrabbed()Z")
	)
	private boolean opencrafter$attackHeldWithoutGrab(boolean grabbed) {
		return grabbed || InputHooks.isSimulatingAttack();
	}
}
