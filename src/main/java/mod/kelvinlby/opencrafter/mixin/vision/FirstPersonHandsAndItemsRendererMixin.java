package mod.kelvinlby.opencrafter.mixin.vision;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.vertex.PoseStack;
import mod.kelvinlby.opencrafter.client.vision.VisionHooks;
import net.minecraft.client.renderer.FirstPersonHandsAndItemsRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.FirstPersonHandsAndItemsRenderState;
import net.minecraft.client.renderer.state.level.PlayerRenderState;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Everything submitted for the first-person hands belongs to the "hand" owner (per hand for arms and held items), whoever calls this. */
@Mixin(FirstPersonHandsAndItemsRenderer.class)
abstract class FirstPersonHandsAndItemsRendererMixin {
	@Inject(method = "submitHandsWithItems", at = @At("HEAD"))
	private void openCrafter$beginHand(float partialTicks, PoseStack poseStack, SubmitNodeCollector collector, PlayerRenderState player,
		FirstPersonHandsAndItemsRenderState hands, CallbackInfo ci) {
		VisionHooks.beginHand();
	}

	@Inject(method = "submitHandsWithItems", at = @At("RETURN"))
	private void openCrafter$endHand(float partialTicks, PoseStack poseStack, SubmitNodeCollector collector, PlayerRenderState player,
		FirstPersonHandsAndItemsRenderState hands, CallbackInfo ci) {
		VisionHooks.endHand();
	}

	// Wraps the whole method, so the owner is popped even when another mixin cancels it early
	@WrapMethod(method = "submitArmWithItem")
	private void openCrafter$handArm(PlayerRenderState playerState, FirstPersonHandsAndItemsRenderState state, float partialTicks, float xRot,
		InteractionHand hand, float attack, ItemStack itemStack, float inverseArmHeight, PoseStack poseStack, SubmitNodeCollector collector, int light,
		Operation<Void> original) {
		VisionHooks.beginHandArm(hand);
		try {
			original.call(playerState, state, partialTicks, xRot, hand, attack, itemStack, inverseArmHeight, poseStack, collector, light);
		} finally {
			VisionHooks.endOwner();
		}
	}
}
