package mod.kelvinlby.opencrafter.mixin.vision;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import mod.kelvinlby.opencrafter.client.vision.VisionHooks;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.Projection;
import net.minecraft.client.renderer.state.GameRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.state.level.PlayerRenderState;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Flow snapshots taken mid-frame: world projection and depth (before the hand clears it), hand projection and depth. */
@Mixin(GameRenderer.class)
abstract class GameRendererMixin {
	@Shadow
	@Final
	private RenderTarget mainRenderTarget;

	@Shadow
	@Final
	private GameRenderState gameRenderState;

	@Shadow
	@Final
	private Projection hudProjection;

	@ModifyArg(method = "renderLevel", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/renderer/ProjectionMatrixBuffer;getBuffer(Lorg/joml/Matrix4f;)Lcom/mojang/renderpearl/api/buffers/GpuBufferSlice;"))
	private Matrix4f openCrafter$captureWorldProjection(Matrix4f projection) {
		VisionHooks.onWorldProjection(projection);
		return projection;
	}

	@Inject(method = "renderLevel", at = @At(value = "INVOKE", shift = At.Shift.AFTER,
		target = "Lnet/minecraft/client/renderer/LevelRenderer;render(Lcom/mojang/blaze3d/resource/GraphicsResourceAllocator;ZLnet/minecraft/client/renderer/state/level/CameraRenderState;Lcom/mojang/renderpearl/api/buffers/GpuBufferSlice;Lorg/joml/Vector4f;ZZ)V"))
	private void openCrafter$afterLevel(CallbackInfo ci) {
		VisionHooks.onLevelRendered(mainRenderTarget, gameRenderState.levelRenderState.cameraRenderState);
	}

	@Inject(method = "render3dHud", at = @At(value = "INVOKE", shift = At.Shift.AFTER,
		target = "Lnet/minecraft/client/renderer/ProjectionMatrixBuffer;getBuffer(Lnet/minecraft/client/renderer/Projection;)Lcom/mojang/renderpearl/api/buffers/GpuBufferSlice;"))
	private void openCrafter$captureHandProjection(CallbackInfo ci) {
		VisionHooks.onHandProjection(hudProjection.getMatrix(new Matrix4f()));
	}

	@WrapOperation(method = "render3dHud", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/renderer/GameRenderer;renderItemInHand(Lnet/minecraft/client/renderer/state/level/CameraRenderState;Lnet/minecraft/client/renderer/state/level/PlayerRenderState;Lcom/mojang/renderpearl/api/textures/GpuTextureView;)V"))
	private void openCrafter$afterHand(GameRenderer self, CameraRenderState camera, PlayerRenderState player, GpuTextureView depth, Operation<Void> original) {
		original.call(self, camera, player, depth);
		VisionHooks.onHandRendered(mainRenderTarget, depth);
	}
}
