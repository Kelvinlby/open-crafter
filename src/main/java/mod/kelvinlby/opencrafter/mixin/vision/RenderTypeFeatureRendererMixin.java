package mod.kelvinlby.opencrafter.mixin.vision;

import com.mojang.blaze3d.vertex.VertexConsumer;
import mod.kelvinlby.opencrafter.client.vision.VisionHooks;
import net.minecraft.client.renderer.feature.FeatureFrameContext;
import net.minecraft.client.renderer.feature.RenderTypeFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/**
 * The one place all model, item, block, text and custom-geometry submits become vertices:
 * track which submit is being built and tee its vertices into the motion recorder.
 */
@Mixin(RenderTypeFeatureRenderer.class)
abstract class RenderTypeFeatureRendererMixin {
	@ModifyArg(method = "prepareGroup", index = 1, at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/renderer/feature/RenderTypeFeatureRenderer;buildGroup(Lnet/minecraft/client/renderer/feature/FeatureFrameContext;Ljava/util/List;)V"))
	private List<?> openCrafter$trackSubmits(List<?> submits) {
		return VisionHooks.trackSubmits(submits);
	}

	@Inject(method = "prepareGroup", at = @At(value = "INVOKE", shift = At.Shift.AFTER,
		target = "Lnet/minecraft/client/renderer/feature/RenderTypeFeatureRenderer;buildGroup(Lnet/minecraft/client/renderer/feature/FeatureFrameContext;Ljava/util/List;)V"))
	private void openCrafter$endSubmits(FeatureFrameContext context, List<?> submits, boolean strictlyOrdered, CallbackInfo ci) {
		VisionHooks.endSubmits();
	}

	@Inject(method = "getVertexBuilder", at = @At("RETURN"), cancellable = true)
	private void openCrafter$recordVertices(RenderType renderType, CallbackInfoReturnable<VertexConsumer> cir) {
		cir.setReturnValue(VisionHooks.wrapVertexBuilder(cir.getReturnValue(), renderType));
	}
}
