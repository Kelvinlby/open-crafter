package mod.kelvinlby.opencrafter.mixin.handview;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
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

	// While a placed block's item morphs into the block, the hand keeps drawing it (also the last one of a stack)
	@ModifyExpressionValue(method = "extractRenderState", at = @At(value = "FIELD",
		target = "Lnet/minecraft/client/player/FirstPersonHandsAndItems;mainHandItem:Lnet/minecraft/world/item/ItemStack;", ordinal = 0))
	private ItemStack openCrafter$shownMain(ItemStack shown) {
		return HandViewHooks.shownItem(InteractionHand.MAIN_HAND, shown);
	}

	@ModifyExpressionValue(method = "extractRenderState", at = @At(value = "FIELD",
		target = "Lnet/minecraft/client/player/FirstPersonHandsAndItems;offHandItem:Lnet/minecraft/world/item/ItemStack;", ordinal = 0))
	private ItemStack openCrafter$shownOff(ItemStack shown) {
		return HandViewHooks.shownItem(InteractionHand.OFF_HAND, shown);
	}

	@Inject(method = "itemUsed", at = @At("HEAD"), cancellable = true)
	private void openCrafter$keepHeight(InteractionHand hand, CallbackInfo ci) {
		if (HandViewHooks.keepsHeightOnUse()) {
			ci.cancel();
		}
	}
}
