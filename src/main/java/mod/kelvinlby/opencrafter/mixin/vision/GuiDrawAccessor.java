package mod.kelvinlby.opencrafter.mixin.vision;

import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.StagedVertexBuffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Reads the private {@code GuiRenderer.Draw} record. */
@Mixin(targets = "net.minecraft.client.gui.render.GuiRenderer$Draw")
interface GuiDrawAccessor {
	@Accessor("draw")
	StagedVertexBuffer.Draw openCrafter$draw();

	@Accessor("pipeline")
	RenderPipeline openCrafter$pipeline();

	@Accessor("textureSetup")
	TextureSetup openCrafter$textureSetup();

	@Accessor("scissorArea")
	ScreenRectangle openCrafter$scissorArea();
}
