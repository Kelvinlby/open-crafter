package mod.kelvinlby.opencrafter.encoder.mixin;

import com.mojang.blaze3d.platform.FramerateLimitTracker;
import mod.kelvinlby.opencrafter.encoder.client.ReplayHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Replay: a headless worker is never "inactive"; frames render as fast as the machine allows. */
@Mixin(FramerateLimitTracker.class)
abstract class FramerateLimitTrackerMixin {
	private static final int UNLIMITED = 260;

	@Inject(method = "getFramerateLimit", at = @At("HEAD"), cancellable = true)
	private void openCrafterEncoder$unlimited(CallbackInfoReturnable<Integer> cir) {
		if (ReplayHooks.active()) {
			cir.setReturnValue(UNLIMITED);
		}
	}
}
