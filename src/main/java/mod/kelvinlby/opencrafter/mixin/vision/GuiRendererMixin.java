package mod.kelvinlby.opencrafter.mixin.vision;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import mod.kelvinlby.opencrafter.client.vision.VisionHooks;
import mod.kelvinlby.opencrafter.client.vision.hud.GuiMaskDraw;
import net.minecraft.client.gui.render.GuiRenderer;
import net.minecraft.client.renderer.StagedVertexBuffer;
import net.minecraft.client.renderer.state.gui.BlitRenderState;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import net.minecraft.client.renderer.state.gui.GuiItemRenderState;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** Records GUI elements as they are built, and replays each draw range into the HUD coverage mask (captured frames only). */
@Mixin(GuiRenderer.class)
abstract class GuiRendererMixin {
	@Shadow
	@Final
	private List<?> draws;

	@Shadow
	@Final
	private StagedVertexBuffer vertexBuffer;

	@WrapOperation(method = "addElementToMesh", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/renderer/state/gui/GuiElementRenderState;buildVertices(Lcom/mojang/blaze3d/vertex/VertexConsumer;)V"))
	private void openCrafter$recordElement(GuiElementRenderState element, VertexConsumer builder, Operation<Void> original) {
		original.call(element, VisionHooks.wrapGuiVertices(element, builder));
	}

	@WrapOperation(method = "submitBlitFromItemAtlas", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/renderer/state/gui/GuiRenderState;addBlitToCurrentLayer(Lnet/minecraft/client/renderer/state/gui/BlitRenderState;)V"))
	private void openCrafter$tagItemIcon(GuiRenderState state, BlitRenderState blit, Operation<Void> original, GuiItemRenderState itemState) {
		VisionHooks.tagItemIcon(blit, itemState.itemStackRenderState().getModelIdentity());
		original.call(state, blit);
	}

	@Inject(method = "executeDrawRange", at = @At("TAIL"))
	private void openCrafter$maskDrawRange(Supplier<String> label, RenderTarget main, GpuBufferSlice dynamicTransforms, int start, int end, CallbackInfo ci) {
		if (VisionHooks.wantsGuiMask()) {
			VisionHooks.onGuiDraws(main, dynamicTransforms, () -> openCrafter$maskDraws(start, end));
		}
	}

	private List<GuiMaskDraw> openCrafter$maskDraws(int start, int end) {
		List<GuiMaskDraw> result = new ArrayList<>(end - start);
		for (int i = start; i < end; i++) {
			GuiDrawAccessor draw = (GuiDrawAccessor) draws.get(i);
			StagedVertexBuffer.ExecuteInfo geometry = vertexBuffer.getExecuteInfo(draw.openCrafter$draw());
			if (geometry != null) {
				result.add(new GuiMaskDraw(draw.openCrafter$pipeline(), geometry, draw.openCrafter$textureSetup(), draw.openCrafter$scissorArea()));
			}
		}
		return result;
	}
}
