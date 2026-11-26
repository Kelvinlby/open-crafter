package mod.kelvinlby.opencrafter.mixin.handview;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import mod.kelvinlby.opencrafter.client.handview.HandViewHooks;
import net.minecraft.client.player.FirstPersonHandsAndItems;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Hand view: the held items' raise animation (skip swapping, old animations, smooth right clicks). */
@Mixin(FirstPersonHandsAndItems.class)
abstract class HandItemsMixin {
	@Shadow
	private ItemStack mainHandItem;

	@Shadow
	private float mainHandHeight;

	@Shadow
	private float offHandHeight;

	@ModifyReturnValue(method = "shouldInstantlyReplaceVisibleItem", at = @At("RETURN"))
	private boolean openCrafter$skipSwap(boolean instantly, @Local(argsOnly = true, ordinal = 0) ItemStack visible,
		@Local(argsOnly = true, ordinal = 1) ItemStack next) {
		return instantly || HandViewHooks.skipsSwap() || HandViewHooks.keepsShownItem(visible, next);
	}

	// mainHandHeight += clamp(target - mainHandHeight, -0.4, 0.4)
	@ModifyArg(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/Mth;clamp(FFF)F", ordinal = 2), index = 0)
	private float openCrafter$mainHeight(float step, @Local(argsOnly = true) LocalPlayer player) {
		return HandViewHooks.mainHeightStep(step, player, mainHandItem, mainHandHeight);
	}

	@ModifyArg(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/Mth;clamp(FFF)F", ordinal = 3), index = 0)
	private float openCrafter$offHeight(float step) {
		return HandViewHooks.offHeightStep(step, offHandHeight);
	}

	@Inject(method = "itemUsed", at = @At("HEAD"), cancellable = true)
	private void openCrafter$keepHeight(InteractionHand hand, CallbackInfo ci) {
		if (HandViewHooks.keepsHeightOnUse()) {
			ci.cancel();
		}
	}
}
