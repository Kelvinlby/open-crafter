package mod.kelvinlby.opencrafter.mixin.handview;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import mod.kelvinlby.opencrafter.client.handview.HandViewHooks;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Hand view: which hand the local player's swings show in and how long they last. Nothing sent changes. */
@Mixin(LivingEntity.class)
abstract class LivingEntitySwingMixin {
	@ModifyVariable(method = "swing(Lnet/minecraft/world/InteractionHand;Lnet/minecraft/world/item/component/SwingAnimation;Z)Z", at = @At("HEAD"), argsOnly = true)
	private InteractionHand openCrafter$swingHand(InteractionHand hand) {
		return HandViewHooks.swingHand((LivingEntity) (Object) this, hand);
	}

	@ModifyReturnValue(method = "getModifiedSwingDuration", at = @At("RETURN"))
	private int openCrafter$swingDuration(int duration) {
		return HandViewHooks.swingDuration((LivingEntity) (Object) this, duration);
	}
}
