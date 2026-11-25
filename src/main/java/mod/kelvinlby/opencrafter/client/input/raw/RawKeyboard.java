package mod.kelvinlby.opencrafter.client.input.raw;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.KeyEvent;
import org.lwjgl.sdl.SDLKeyboard;

/**
 * Delivers synthetic physical key presses through {@code KeyboardHandler.keyPress}, the exact
 * entry point the SDL event handler uses, so screens, the pause menu, global F-keys and key
 * mappings react identically to a real key.
 */
public final class RawKeyboard {
	/** SDLK_SCANCODE_MASK: keycodes of non-character keys are the scancode with this bit set. */
	private static final int SCANCODE_MASK = 1 << 30;

	private RawKeyboard() {
	}

	/** Key down immediately followed by key up. Main thread only. */
	public static void press(int scancode, int modifiers) {
		KeyEvent event = new KeyEvent(scancode, keycodeFor(scancode, modifiers), modifiers);
		deliver(InputConstants.PRESS, event);
		deliver(InputConstants.RELEASE, event);
	}

	private static void deliver(int action, KeyEvent event) {
		Minecraft minecraft = Minecraft.getInstance();
		minecraft.keyboardHandler.keyPress(minecraft.getWindow().handle(), action, event);
	}

	/** The layout-dependent keycode SDL would put into a key event for this scancode. */
	private static int keycodeFor(int scancode, int modifiers) {
		int keycode = SDLKeyboard.SDL_GetKeyFromScancode(scancode, (short) modifiers, true);
		return keycode != 0 ? keycode : scancode | SCANCODE_MASK;
	}
}
