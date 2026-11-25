package mod.kelvinlby.opencrafter.mixin.vision;

import mod.kelvinlby.opencrafter.client.vision.VisionHooks;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.renderer.state.level.QuadParticleRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Notes the owner of every quad in emission order (per layer). */
@Mixin(QuadParticleRenderState.class)
abstract class QuadParticleRenderStateMixin {
	@Inject(method = "add", at = @At("HEAD"))
	private void openCrafter$noteQuad(SingleQuadParticle.Layer layer, float x, float y, float z, float xRot, float yRot, float zRot, float wRot,
		float scale, float u0, float u1, float v0, float v1, int color, int lightCoords, CallbackInfo ci) {
		VisionHooks.onParticleQuad(this, layer);
	}
}
