package mod.kelvinlby.opencrafter.mixin.vision;

import com.mojang.blaze3d.vertex.PoseStack;
import mod.kelvinlby.opencrafter.client.vision.VisionHooks;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Remembers which entity each render state belongs to and owns the submits made while it is submitted. */
@Mixin(EntityRenderDispatcher.class)
abstract class EntityRenderDispatcherMixin {
	@Inject(method = "extractEntity", at = @At("RETURN"))
	private <E extends Entity> void openCrafter$rememberEntity(E entity, float partialTicks, CallbackInfoReturnable<EntityRenderState> cir) {
		VisionHooks.onEntityExtracted(cir.getReturnValue(), entity.getId());
	}

	@Inject(method = "submit", at = @At("HEAD"))
	private <S extends EntityRenderState> void openCrafter$beginEntity(S state, CameraRenderState camera, double x, double y, double z,
		PoseStack poseStack, SubmitNodeCollector collector, CallbackInfo ci) {
		VisionHooks.beginEntity(state);
	}

	@Inject(method = "submit", at = @At("RETURN"))
	private <S extends EntityRenderState> void openCrafter$endEntity(S state, CameraRenderState camera, double x, double y, double z,
		PoseStack poseStack, SubmitNodeCollector collector, CallbackInfo ci) {
		VisionHooks.endOwner();
	}
}
