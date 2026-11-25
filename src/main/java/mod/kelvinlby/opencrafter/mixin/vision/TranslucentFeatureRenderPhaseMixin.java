package mod.kelvinlby.opencrafter.mixin.vision;

import mod.kelvinlby.opencrafter.client.vision.VisionHooks;
import net.minecraft.client.renderer.feature.phase.TranslucentFeatureRenderPhase;
import net.minecraft.client.renderer.feature.submit.SubmitNode;
import net.minecraft.client.renderer.feature.submit.TranslucentSubmit;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Translucent counterpart of {@link SimpleFeatureRenderPhaseMixin}. */
@Mixin(TranslucentFeatureRenderPhase.class)
abstract class TranslucentFeatureRenderPhaseMixin {
	@Inject(method = "submit", at = @At("HEAD"))
	private void openCrafter$tagSubmit(TranslucentSubmit submit, CallbackInfo ci) {
		if (submit instanceof SubmitNode node) {
			VisionHooks.onSubmit(node);
		}
	}
}
