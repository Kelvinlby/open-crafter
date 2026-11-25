package mod.kelvinlby.opencrafter.client.vision.hud;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.StagedVertexBuffer;
import net.minecraft.client.renderer.state.WindowRenderState;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Builds the HUD coverage mask by replaying the GUI's own draw calls (same vertex buffers,
 * projection and scissor) into an R8 target with a pipeline that writes 1 wherever the GUI
 * fragment is not (nearly) transparent. Overlays that tint the world rather than cover it
 * are skipped.
 */
public final class GuiMaskRenderer {
	private static final Set<RenderPipeline> SEE_THROUGH_OVERLAYS = Set.of(RenderPipelines.VIGNETTE, RenderPipelines.GUI_NAUSEA_OVERLAY);

	private final GuiMaskPipelines pipelines = new GuiMaskPipelines();

	public void replay(GpuTextureView mask, GpuBufferSlice dynamicTransforms, List<GuiMaskDraw> draws) {
		try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder()
				.createRenderPass(() -> "open-crafter hud mask", mask, Optional.empty())) {
			RenderSystem.bindDefaultUniforms(pass);
			pass.setUniform("DynamicTransforms", dynamicTransforms);
			for (GuiMaskDraw draw : draws) {
				if (!SEE_THROUGH_OVERLAYS.contains(draw.pipeline())) {
					replayDraw(pass, draw);
				}
			}
		}
	}

	private void replayDraw(RenderPass pass, GuiMaskDraw draw) {
		GpuTextureView texture = draw.textures().texure0();
		pass.setPipeline(RenderSystem.getCompiledPipeline(pipelines.forDraw(draw.pipeline(), texture != null)));
		if (texture != null) {
			pass.setUniform("Sampler0", texture, draw.textures().sampler0());
		}
		applyScissor(pass, draw.scissor());
		StagedVertexBuffer.ExecuteInfo geometry = draw.geometry();
		pass.setVertexBuffer(0, geometry.vertexBuffer().slice());
		pass.setIndexBuffer(geometry.indexBuffer(), geometry.indexType());
		pass.drawIndexed(geometry.indexCount(), 1, geometry.firstIndex(), geometry.baseVertex(), 0);
	}

	/** Same arithmetic as {@code GuiRenderer.enableScissor}. */
	private static void applyScissor(RenderPass pass, ScreenRectangle rectangle) {
		if (rectangle == null) {
			pass.disableScissor();
			return;
		}
		WindowRenderState window = Minecraft.getInstance().gameRenderer.gameRenderState().windowRenderState;
		int scale = window.guiScale;
		double left = rectangle.left() * scale;
		double top = rectangle.top() * scale;
		double right = Math.min(rectangle.right() * scale, window.width);
		double bottom = Math.min(rectangle.bottom() * scale, window.height);
		pass.enableScissor((int) left, window.height - (int) bottom, Math.max(0, (int) (right - left)), Math.max(0, (int) (bottom - top)));
	}
}
