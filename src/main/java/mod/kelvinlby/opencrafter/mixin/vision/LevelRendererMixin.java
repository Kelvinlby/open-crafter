package mod.kelvinlby.opencrafter.mixin.vision;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import mod.kelvinlby.opencrafter.client.vision.VisionHooks;
import mod.kelvinlby.opencrafter.client.vision.record.RecordPhase;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Marks the main level submission so only its submits (not shadow or GUI passes) are recorded as world geometry. */
@Mixin(LevelRenderer.class)
abstract class LevelRendererMixin {
	@WrapOperation(method = "render", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/renderer/LevelRenderer;submitFeatures(Lnet/minecraft/client/renderer/state/level/LevelRenderState;Lnet/minecraft/client/renderer/SubmitNodeCollector;Z)V"))
	private void openCrafter$worldPhase(LevelRenderer self, LevelRenderState state, SubmitNodeCollector collector, boolean renderOutline, Operation<Void> original) {
		VisionHooks.beginPhase(RecordPhase.WORLD);
		try {
			original.call(self, state, collector, renderOutline);
		} finally {
			VisionHooks.endPhase();
		}
	}
}
