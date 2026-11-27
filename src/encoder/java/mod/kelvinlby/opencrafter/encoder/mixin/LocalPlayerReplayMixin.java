package mod.kelvinlby.opencrafter.encoder.mixin;

import mod.kelvinlby.opencrafter.encoder.client.ReplayHooks;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Replay: the player's movement packets are the recorded ones. */
@Mixin(LocalPlayer.class)
abstract class LocalPlayerReplayMixin {
	@Inject(method = "sendChanges", at = @At("HEAD"), cancellable = true)
	private void openCrafterEncoder$sendChanges(CallbackInfo ci) {
		if (ReplayHooks.replaceSendChanges((LocalPlayer) (Object) this)) {
			ci.cancel();
		}
	}
}
