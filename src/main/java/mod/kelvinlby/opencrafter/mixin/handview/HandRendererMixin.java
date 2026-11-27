package mod.kelvinlby.opencrafter.mixin.handview;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import mod.kelvinlby.opencrafter.client.handview.HandViewHooks;
import net.minecraft.client.renderer.FirstPersonHandsAndItemsRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.state.level.FirstPersonHandsAndItemsRenderState;
import net.minecraft.client.renderer.state.level.PlayerRenderState;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.PlayerSkin;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hand view: the first-person hands' pose and swing. Only chaining injectors (no redirects or
 * overwrites), so other hand mods such as Singularity's Hand View apply alongside.
 */
@Mixin(FirstPersonHandsAndItemsRenderer.class)
abstract class HandRendererMixin {
	@Inject(method = "submitHandsWithItems", at = @At("HEAD"))
	private void openCrafter$beginHands(float partialTicks, PoseStack poseStack, SubmitNodeCollector collector, PlayerRenderState player,
		FirstPersonHandsAndItemsRenderState hands, CallbackInfo ci) {
		HandViewHooks.beginHands(partialTicks, poseStack);
	}

	@Inject(method = "submitHandsWithItems", at = @At("TAIL"))
	private void openCrafter$endHands(float partialTicks, PoseStack poseStack, SubmitNodeCollector collector, PlayerRenderState player,
		FirstPersonHandsAndItemsRenderState hands, CallbackInfo ci) {
		HandViewHooks.endHands(poseStack, collector, player);
	}

	// The hands' lag behind turns: (view - bob) * 0.1 about X, then Y
	@ModifyArg(method = "submitHandsWithItems", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;rotateDegrees(Lcom/mojang/math/Axis;F)V", ordinal = 0), index = 1)
	private float openCrafter$pitchDelay(float degrees) {
		return HandViewHooks.rotationDelay(degrees);
	}

	@ModifyArg(method = "submitHandsWithItems", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;rotateDegrees(Lcom/mojang/math/Axis;F)V", ordinal = 1), index = 1)
	private float openCrafter$yawDelay(float degrees) {
		return HandViewHooks.rotationDelay(degrees);
	}

	@Inject(method = "submitArmWithItem", at = @At("HEAD"), cancellable = true)
	private void openCrafter$hideEmptyHand(PlayerRenderState playerState, FirstPersonHandsAndItemsRenderState state, float partialTicks, float xRot,
		InteractionHand hand, float attack, ItemStack itemStack, float inverseArmHeight, PoseStack poseStack, SubmitNodeCollector collector, int light,
		CallbackInfo ci) {
		if (HandViewHooks.hidesHand(itemStack)) {
			ci.cancel();
		}
	}

	// Right after the hand's own pose was pushed, before anything places the arm or item
	@Inject(method = "submitArmWithItem", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;pushPose()V", ordinal = 0, shift = At.Shift.AFTER))
	private void openCrafter$beginHand(PlayerRenderState playerState, FirstPersonHandsAndItemsRenderState state, float partialTicks, float xRot,
		InteractionHand hand, float attack, ItemStack itemStack, float inverseArmHeight, PoseStack poseStack, SubmitNodeCollector collector, int light,
		CallbackInfo ci) {
		HandViewHooks.beginHand(hand, poseStack);
	}

	// The float arguments are partialTicks, xRot, attack, inverseArmHeight
	@ModifyVariable(method = "submitArmWithItem", at = @At("HEAD"), argsOnly = true, ordinal = 2)
	private float openCrafter$attack(float attack, @Local(argsOnly = true) InteractionHand hand) {
		return HandViewHooks.attack(hand, attack);
	}

	@ModifyExpressionValue(method = "submitArmWithItem", at = @At(value = "FIELD",
		target = "Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;currentSwing:Lnet/minecraft/world/entity/LivingEntity$SwingDescription;"))
	private LivingEntity.SwingDescription openCrafter$currentSwing(LivingEntity.SwingDescription swing, @Local(argsOnly = true) InteractionHand hand) {
		return HandViewHooks.currentSwing(hand, swing);
	}

	@WrapOperation(method = "submitArmWithItem", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/renderer/item/ItemStackRenderState;submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;III)V"))
	private void openCrafter$item(ItemStackRenderState item, PoseStack poseStack, SubmitNodeCollector collector, int light, int overlay, int outline,
		Operation<Void> original, @Local(argsOnly = true) InteractionHand hand) {
		HandViewHooks.beforeItem(hand, poseStack, item);
		original.call(item, poseStack, collector, light, overlay, outline);
	}

	// Every place a held item is put by the arm; the hook leaves uses alone
	@WrapOperation(method = "submitArmWithItem", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/renderer/FirstPersonHandsAndItemsRenderer;applyItemArmTransform(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/world/entity/HumanoidArm;F)V"))
	private void openCrafter$itemArm(FirstPersonHandsAndItemsRenderer self, PoseStack poseStack, HumanoidArm arm, float inverseArmHeight, Operation<Void> original,
		@Local(argsOnly = true) InteractionHand hand, @Local(argsOnly = true) PlayerRenderState playerState,
		@Local(argsOnly = true) FirstPersonHandsAndItemsRenderState hands) {
		HandViewHooks.beforeItemArm(poseStack, arm, HandViewHooks.inUse(playerState, hands, hand));
		original.call(self, poseStack, arm, inverseArmHeight);
	}

	@Inject(method = "renderPlayerArm", at = @At("HEAD"))
	private void openCrafter$arm(PoseStack poseStack, SubmitNodeCollector collector, int light, float inverseArmHeight, float attackValue, HumanoidArm arm,
		PlayerRenderState playerState, CallbackInfo ci) {
		HandViewHooks.beforeArm(poseStack, arm);
	}

	// Both the arm model (wide or slim) and its texture come from the render state's skin; swapped only for this call
	@WrapMethod(method = "renderPlayerHand")
	private void openCrafter$armSkin(PoseStack poseStack, SubmitNodeCollector collector, int light, HumanoidArm arm, PlayerRenderState playerState,
		Operation<Void> original) {
		AvatarRenderState avatar = playerState.avatarRenderState;
		PlayerSkin skin = avatar == null ? null : avatar.skin;
		if (skin != null) {
			avatar.skin = HandViewHooks.armSkin(skin);
		}
		try {
			original.call(poseStack, collector, light, arm, playerState);
		} finally {
			if (skin != null) {
				avatar.skin = skin;
			}
		}
	}

	@Inject(method = "applyEatTransform", at = @At("HEAD"))
	private void openCrafter$eat(PoseStack poseStack, float partialTicks, HumanoidArm arm, float remaining, int useDuration, CallbackInfo ci) {
		HandViewHooks.beforeEat(poseStack, arm, remaining, useDuration);
	}

	// Keeps the bob, leaves out the move into the middle
	@Inject(method = "applyEatTransform", at = @At(value = "INVOKE", target = "Ljava/lang/Math;pow(DD)D"), cancellable = true)
	private void openCrafter$noEat(PoseStack poseStack, float partialTicks, HumanoidArm arm, float remaining, int useDuration, CallbackInfo ci) {
		if (HandViewHooks.eatDisabled()) {
			ci.cancel();
		}
	}

	@WrapOperation(method = "applyEatTransform", at = @At(value = "INVOKE", target = "Ljava/lang/Math;pow(DD)D"))
	private double openCrafter$eatCurve(double left, double exponent, Operation<Double> original, @Local(argsOnly = true) int useDuration,
		@Local(argsOnly = true) HumanoidArm arm) {
		return HandViewHooks.eatPow(left, exponent, useDuration, arm, (a, b) -> original.call(a, b));
	}

	// The up-and-down bob, translate(0, h, 0)
	@ModifyArg(method = "applyEatTransform", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;translate(FFF)V", ordinal = 0), index = 1)
	private float openCrafter$eatBob(float bob, @Local(argsOnly = true, ordinal = 0) float partialTicks, @Local(argsOnly = true, ordinal = 1) float remaining,
		@Local(argsOnly = true) int useDuration, @Local(argsOnly = true) HumanoidArm arm) {
		return HandViewHooks.eatBob(bob, remaining, partialTicks, useDuration, arm);
	}
}
