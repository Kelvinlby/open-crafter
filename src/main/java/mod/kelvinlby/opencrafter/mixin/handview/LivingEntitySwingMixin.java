package mod.kelvinlby.opencrafter.mixin.handview;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import mod.kelvinlby.opencrafter.client.handview.HandViewHooks;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.component.SwingAnimation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Hand view: which hand the local player's swings show in, how long they last, and each swing
 * that started (so it can be told apart from the actions that drive the hand). Nothing sent changes.
 */
@Mixin(LivingEntity.class)
abstract class LivingEntitySwingMixin {
	private static final String SWING = "swing(Lnet/minecraft/world/InteractionHand;Lnet/minecraft/world/item/component/SwingAnimation;Z)Z";

	@ModifyVariable(method = SWING, at = @At("HEAD"), argsOnly = true)
	private InteractionHand openCrafter$swingHand(InteractionHand hand) {
		return HandViewHooks.swingHand((LivingEntity) (Object) this, hand);
	}

	// The hand argument is the shown one here: the modifier above wrote it back
	@Inject(method = SWING, at = @At("RETURN"))
	private void openCrafter$swung(InteractionHand hand, SwingAnimation animation, boolean send, CallbackInfoReturnable<Boolean> cir) {
		HandViewHooks.onSwing((LivingEntity) (Object) this, hand, cir.getReturnValueZ());
	}

	@ModifyReturnValue(method = "getModifiedSwingDuration", at = @At("RETURN"))
	private int openCrafter$swingDuration(int duration) {
		return HandViewHooks.swingDuration((LivingEntity) (Object) this, duration);
	}
}
