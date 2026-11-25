package mod.kelvinlby.opencrafter.mixin.input;

import mod.kelvinlby.opencrafter.client.input.InputHooks;
import net.minecraft.client.KeyMapping;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** {@code setAll} re-syncs mappings from the physical keyboard; re-apply simulated holds right after. */
@Mixin(KeyMapping.class)
abstract class KeyMappingMixin {
	@Inject(method = "setAll", at = @At("TAIL"))
	private static void opencrafter$afterSetAll(CallbackInfo ci) {
		InputHooks.afterKeyStatesResynced();
	}
}
