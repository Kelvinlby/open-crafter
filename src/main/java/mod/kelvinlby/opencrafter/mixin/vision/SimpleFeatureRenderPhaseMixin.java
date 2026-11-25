package mod.kelvinlby.opencrafter.mixin.vision;

import mod.kelvinlby.opencrafter.client.vision.VisionHooks;
import net.minecraft.client.renderer.feature.phase.SimpleFeatureRenderPhase;
import net.minecraft.client.renderer.feature.submit.SubmitNode;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Every submit node passes through a render phase: the single point where it gets its key. */
@Mixin(SimpleFeatureRenderPhase.class)
abstract class SimpleFeatureRenderPhaseMixin {
	@Inject(method = "submit", at = @At("HEAD"))
	private void openCrafter$tagSubmit(SubmitNode submit, CallbackInfo ci) {
		VisionHooks.onSubmit(submit);
	}
}
