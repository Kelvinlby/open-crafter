package mod.kelvinlby.opencrafter.client.vision.gui;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.state.WindowRenderState;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/** Records the GUI elements of captured frames as vanilla builds their vertices. Render thread only. */
public final class GuiElementRecorder {
	private List<GuiElement> elements = new ArrayList<>();
	private final Map<GuiElementRenderState, Long> itemIdentities = new IdentityHashMap<>();
	private boolean recording;

	public void beginFrame(boolean capturing) {
		recording = capturing;
		elements = new ArrayList<>();
		itemIdentities.clear();
	}

	/** Marks the blit vanilla made for an item icon with the item's model identity. */
	public void tagItemIcon(GuiElementRenderState blit, Object modelIdentity) {
		if (recording) {
			itemIdentities.put(blit, (long) modelIdentity.hashCode() | 1L << 40);
		}
	}

	public GuiFrame finishFrame(int width, int height) {
		recording = false;
		return new GuiFrame(elements, width, height);
	}

	public VertexConsumer wrap(GuiElementRenderState state, VertexConsumer builder) {
		if (!recording) {
			return builder;
		}
		WindowRenderState window = Minecraft.getInstance().gameRenderer.gameRenderState().windowRenderState;
		float scale = window.guiScale;
		Long itemIdentity = itemIdentities.remove(state);
		GuiElement element = newElement(elements.size(), state, scale, window.width, window.height, itemIdentity == null ? 0L : itemIdentity);
		elements.add(element);
		return new RecordingGuiVertexConsumer(builder, element, scale);
	}

	private static GuiElement newElement(int order, GuiElementRenderState state, float scale, int width, int height, long itemIdentity) {
		RenderPipeline pipeline = state.pipeline();
		TextureSetup textures = state.textureSetup();
		return new GuiElement(order, pipeline, pipeline.getPrimitiveTopology(), textures.texure0(), textures.sampler0(),
			clip(state.scissorArea(), scale, width, height), itemIdentity);
	}

	private static float[] clip(ScreenRectangle scissor, float scale, int width, int height) {
		if (scissor == null) {
			return new float[] {0, 0, width, height};
		}
		return new float[] {
			Math.max(0, scissor.left() * scale), Math.max(0, scissor.top() * scale),
			Math.min(width, scissor.right() * scale), Math.min(height, scissor.bottom() * scale)
		};
	}
}
