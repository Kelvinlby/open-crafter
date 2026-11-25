package mod.kelvinlby.opencrafter.mixin.vision;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import mod.kelvinlby.opencrafter.client.vision.VisionHooks;
import mod.kelvinlby.opencrafter.client.vision.hud.GuiMaskDraw;
import net.minecraft.client.gui.render.GuiRenderer;
import net.minecraft.client.renderer.StagedVertexBuffer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** After each GUI draw range, replays the same draws into the HUD coverage mask (captured frames only). */
@Mixin(GuiRenderer.class)
abstract class GuiRendererMixin {
	@Shadow
	@Final
	private List<?> draws;

	@Shadow
	@Final
	private StagedVertexBuffer vertexBuffer;

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
