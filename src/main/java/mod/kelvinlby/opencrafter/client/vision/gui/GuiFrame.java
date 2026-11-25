package mod.kelvinlby.opencrafter.client.vision.gui;

import java.util.List;

/** Every GUI element built in one captured frame, in draw order (later = on top). */
public record GuiFrame(List<GuiElement> elements, int width, int height) {
	public static final GuiFrame EMPTY = new GuiFrame(List.of(), 0, 0);
}
