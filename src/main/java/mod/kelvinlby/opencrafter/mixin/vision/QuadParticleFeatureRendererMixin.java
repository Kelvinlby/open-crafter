package mod.kelvinlby.opencrafter.mixin.vision;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.VertexConsumer;
import mod.kelvinlby.opencrafter.client.vision.VisionHooks;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.renderer.feature.QuadParticleFeatureRenderer;
import net.minecraft.client.renderer.state.level.QuadParticleRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Tees particle vertices into the motion recorder. */
@Mixin(QuadParticleFeatureRenderer.class)
abstract class QuadParticleFeatureRendererMixin {
	@WrapOperation(method = "prepareGroup", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/renderer/state/level/QuadParticleRenderState;buildLayer(Lnet/minecraft/client/particle/SingleQuadParticle$Layer;Lcom/mojang/blaze3d/vertex/VertexConsumer;)V"))
	private void openCrafter$recordLayer(QuadParticleRenderState state, SingleQuadParticle.Layer layer, VertexConsumer builder, Operation<Void> original) {
		original.call(state, layer, VisionHooks.wrapParticleLayer(state, layer, builder));
	}
}
