package mod.kelvinlby.opencrafter.mixin.vision;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import mod.kelvinlby.opencrafter.client.vision.VisionHooks;
import net.minecraft.client.Camera;
import net.minecraft.client.particle.QuadParticleGroup;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.renderer.state.level.QuadParticleRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Each particle owns the quads it extracts, so they can be matched up in the next captured frame. */
@Mixin(QuadParticleGroup.class)
abstract class QuadParticleGroupMixin {
	@WrapOperation(method = "*", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/particle/SingleQuadParticle;extract(Lnet/minecraft/client/renderer/state/level/QuadParticleRenderState;Lnet/minecraft/client/Camera;F)V"))
	private void openCrafter$ownQuads(SingleQuadParticle particle, QuadParticleRenderState state, Camera camera, float partialTick, Operation<Void> original) {
		VisionHooks.beginParticle(particle);
		try {
			original.call(particle, state, camera, partialTick);
		} finally {
			VisionHooks.endParticle();
		}
	}
}
