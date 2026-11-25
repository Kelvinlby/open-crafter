package mod.kelvinlby.opencrafter.client.input.raw;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

import java.util.HashMap;
import java.util.Map;

/**
 * Types text through {@code KeyboardHandler.textInput}, the SDL text-input entry point: the
 * text is split into code points and each becomes a {@code CharacterEvent} for the open screen.
 */
public final class TextTyper {
	private TextTyper() {
	}

	/** Main thread only. Returns whether a screen received the text and how many code points were sent. */
	public static Map<String, Object> type(String text) {
		Minecraft minecraft = Minecraft.getInstance();
		Screen screen = minecraft.gui.screen();
		boolean received = screen != null && minecraft.gui.overlay() == null;
		if (received && !text.isEmpty()) {
			minecraft.keyboardHandler.textInput(minecraft.getWindow().handle(), text);
		}
		Map<String, Object> result = new HashMap<>();
		result.put("received", received);
		result.put("codepoints", received ? text.codePointCount(0, text.length()) : 0);
		result.put("screen", screen == null ? null : screen.getClass().getSimpleName());
		return result;
	}
}
