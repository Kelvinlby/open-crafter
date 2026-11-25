package mod.kelvinlby.opencrafter.mixin.vision;

import com.mojang.blaze3d.vertex.PoseStack;
import mod.kelvinlby.opencrafter.client.vision.VisionHooks;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Block entities (chests, signs, pistons, ...) own the submits made while they are submitted. */
@Mixin(BlockEntityRenderDispatcher.class)
abstract class BlockEntityRenderDispatcherMixin {
	@Inject(method = "submit", at = @At("HEAD"))
	private <S extends BlockEntityRenderState> void openCrafter$beginBlockEntity(S state, PoseStack poseStack, SubmitNodeCollector collector,
		CameraRenderState camera, CallbackInfo ci) {
		VisionHooks.beginBlockEntity(state.blockPos.asLong());
	}

	@Inject(method = "submit", at = @At("RETURN"))
	private <S extends BlockEntityRenderState> void openCrafter$endBlockEntity(S state, PoseStack poseStack, SubmitNodeCollector collector,
		CameraRenderState camera, CallbackInfo ci) {
		VisionHooks.endOwner();
	}
}
