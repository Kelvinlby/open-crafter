package mod.kelvinlby.opencrafter.client.input.raw;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.input.InputQuirks;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Parses physical key names and modifier names for {@code input.screen_key}. */
public final class ScreenKeys {
	private static final String KEYBOARD_PREFIX = "key.keyboard.";
	private static final Map<String, String> ALIASES = Map.ofEntries(
		Map.entry("esc", "escape"),
		Map.entry("return", "enter"),
		Map.entry("del", "delete"),
		Map.entry("ins", "insert"),
		Map.entry("pageup", "page.up"),
		Map.entry("pagedown", "page.down"),
		Map.entry("arrow.up", "up"),
		Map.entry("arrow.down", "down"),
		Map.entry("arrow.left", "left"),
		Map.entry("arrow.right", "right")
	);
	/** SDL_KMOD_* left-hand variants, as found in real key events. */
	private static final int MOD_LSHIFT = 0x0001;
	private static final int MOD_LCTRL = 0x0040;
	private static final int MOD_LALT = 0x0100;
	private static final int MOD_LGUI = 0x0400;

	private ScreenKeys() {
	}

	/**
	 * Scancode for a vanilla key name without prefix ({@code escape}, {@code page_up},
	 * {@code left.shift}, {@code a}), a full {@code key.keyboard.*} name, or an alias.
	 */
	public static int scancode(String name) {
		InputConstants.Key key = parseKey(name);
		if (key.getType() != InputConstants.Type.KEYBOARD || key.getValue() == InputConstants.UNKNOWN.getValue()) {
			throw new IllegalArgumentException("Not a keyboard key: " + name);
		}
		return key.getValue();
	}

	/** Modifier bit mask for names {@code shift, ctrl, alt, super} and {@code shortcut} (ctrl, or cmd on macOS). */
	public static int modifiers(List<?> names) {
		int mask = 0;
		for (Object name : names) {
			mask |= modifier(String.valueOf(name).toLowerCase(Locale.ROOT));
		}
		return mask;
	}

	private static int modifier(String name) {
		return switch (name) {
			case "shift" -> MOD_LSHIFT;
			case "ctrl", "control" -> MOD_LCTRL;
			case "alt", "option" -> MOD_LALT;
			case "super", "cmd", "command", "meta", "win" -> MOD_LGUI;
			case "shortcut" -> InputQuirks.REPLACE_CTRL_KEY_WITH_CMD_KEY ? MOD_LGUI : MOD_LCTRL;
			default -> throw new IllegalArgumentException("Unknown modifier: " + name);
		};
	}

	private static InputConstants.Key parseKey(String name) {
		String fullName = fullName(name);
		try {
			return InputConstants.getKey(fullName);
		} catch (IllegalArgumentException e) {
			throw new IllegalArgumentException("Unknown key: " + name);
		}
	}

	private static String fullName(String name) {
		String normalized = name.toLowerCase(Locale.ROOT).replace('_', '.');
		if (normalized.startsWith(KEYBOARD_PREFIX)) {
			return normalized;
		}
		return KEYBOARD_PREFIX + ALIASES.getOrDefault(normalized, normalized);
	}
}
