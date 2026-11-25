package mod.kelvinlby.opencrafter.client.vision.hud;

import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.StagedVertexBuffer;
import org.jspecify.annotations.Nullable;

/** One GUI draw call as vanilla executed it, to be replayed into the HUD mask. */
public record GuiMaskDraw(
	RenderPipeline pipeline,
	StagedVertexBuffer.ExecuteInfo geometry,
	TextureSetup textures,
	@Nullable ScreenRectangle scissor
) {
}
