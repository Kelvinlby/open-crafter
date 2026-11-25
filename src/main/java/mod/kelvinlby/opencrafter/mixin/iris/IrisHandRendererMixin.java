package mod.kelvinlby.opencrafter.mixin.iris;

import mod.kelvinlby.opencrafter.client.vision.VisionHooks;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * With a shader pack, Iris draws the first-person hand itself (inside the level pass, with its
 * own projection) and turns the vanilla hand pass into a no-op. Snapshot the depth around its
 * solid hand pass and take its projection, so hand flow stays exact.
 */
@Pseudo
@Mixin(targets = "net.irisshaders.iris.pathways.HandRenderer")
abstract class IrisHandRendererMixin {
	@Inject(method = "renderSolid", at = @At("HEAD"))
	private void openCrafter$beforeSolidHand(CallbackInfo ci) {
		VisionHooks.beforeShaderHand();
	}

	@Inject(method = "renderSolid", at = @At("RETURN"))
	private void openCrafter$afterSolidHand(CallbackInfo ci) {
		VisionHooks.afterShaderHand();
	}

	@ModifyArg(method = "setupGlState", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/renderer/ProjectionMatrixBuffer;getBuffer(Lorg/joml/Matrix4f;)Lcom/mojang/renderpearl/api/buffers/GpuBufferSlice;"))
	private Matrix4f openCrafter$handProjection(Matrix4f projection) {
		VisionHooks.onShaderHandProjection(projection);
		return projection;
	}
}
