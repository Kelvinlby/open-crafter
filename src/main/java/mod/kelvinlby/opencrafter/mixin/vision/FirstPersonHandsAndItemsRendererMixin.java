package mod.kelvinlby.opencrafter.mixin.vision;

import com.mojang.blaze3d.vertex.PoseStack;
import mod.kelvinlby.opencrafter.client.vision.VisionHooks;
import net.minecraft.client.renderer.FirstPersonHandsAndItemsRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.FirstPersonHandsAndItemsRenderState;
import net.minecraft.client.renderer.state.level.PlayerRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Everything submitted for the first-person hands belongs to the "hand" owner, whoever calls this. */
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
}
