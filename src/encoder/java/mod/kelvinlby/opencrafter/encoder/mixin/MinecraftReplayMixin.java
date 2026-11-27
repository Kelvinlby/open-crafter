package mod.kelvinlby.opencrafter.encoder.mixin;

import mod.kelvinlby.opencrafter.encoder.client.ReplayHooks;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Replay: the game loop, client tick and key handling of a worker client. */
@Mixin(Minecraft.class)
abstract class MinecraftReplayMixin {
	@Inject(method = "runTick", at = @At("HEAD"))
	private void openCrafterEncoder$loopStart(boolean advanceGameTime, CallbackInfo ci) {
		ReplayHooks.onLoopStart();
	}

	@Inject(method = "tick", at = @At("HEAD"))
	private void openCrafterEncoder$tick(CallbackInfo ci) {
		ReplayHooks.onClientTick();
	}

	@Inject(method = "handleKeybinds", at = @At("HEAD"), cancellable = true)
	private void openCrafterEncoder$keybinds(CallbackInfo ci) {
		if (ReplayHooks.replaceKeybinds()) {
			ci.cancel();
		}
	}

	@Inject(method = "renderFrame", at = @At("HEAD"))
	private void openCrafterEncoder$beforeRender(boolean advanceGameTime, CallbackInfo ci) {
		ReplayHooks.beforeRender();
	}

	@Inject(method = "renderFrame", at = @At("TAIL"))
	private void openCrafterEncoder$afterRender(boolean advanceGameTime, CallbackInfo ci) {
		ReplayHooks.afterRender();
	}
}
