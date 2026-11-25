package mod.kelvinlby.opencrafter.mixin.vision;

import mod.kelvinlby.opencrafter.client.vision.VisionHooks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Frame boundaries: decide capture at the start, capture right after the final image is rendered. */
@Mixin(Minecraft.class)
abstract class MinecraftRenderFrameMixin {
	@Shadow
	@Final
	public GameRenderer gameRenderer;

	@Inject(method = "renderFrame", at = @At("HEAD"))
	private void openCrafter$onFrameStart(boolean advanceGameTime, CallbackInfo ci) {
		VisionHooks.onFrameStart();
	}

	@Inject(method = "renderFrame", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/GameRenderer;render()V", shift = At.Shift.AFTER))
	private void openCrafter$onFrameRendered(boolean advanceGameTime, CallbackInfo ci) {
		VisionHooks.onFrameRendered(gameRenderer.mainRenderTarget());
	}
}
