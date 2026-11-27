package mod.kelvinlby.opencrafter.mixin.contact;

import mod.kelvinlby.opencrafter.client.contact.ContactHooks;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Contact effects: temporary block models are submitted with the level's own features (after block entities). */
@Mixin(LevelRenderer.class)
abstract class LevelRendererContactMixin {
	@Inject(method = "submitFeatures", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/renderer/LevelRenderer;submitBlockDestroyAnimation(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/LevelRenderState;)V"))
	private void openCrafter$submitContactBlocks(LevelRenderState state, SubmitNodeCollector collector, boolean renderOutline, CallbackInfo ci) {
		ContactHooks.submitWorld(collector, state.cameraRenderState.pos);
	}
}
