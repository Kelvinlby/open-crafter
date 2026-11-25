package mod.kelvinlby.opencrafter.client.input.keys;

import com.mojang.blaze3d.platform.InputConstants;
import mod.kelvinlby.opencrafter.mixin.input.KeyMappingAccessor;
import mod.kelvinlby.opencrafter.mixin.input.ToggleKeyMappingAccessor;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.ToggleKeyMapping;

import java.util.Optional;

/** Lookup and low-level state access for {@link KeyMapping}s, including ones added by other mods. */
public final class KeyMappings {
	private static final String PREFIX = "key.";

	private KeyMappings() {
	}

	/** Resolves a full id ({@code key.forward}) or a short one ({@code forward}). */
	public static Optional<KeyMapping> find(String id) {
		KeyMapping exact = KeyMapping.get(id);
		return exact != null ? Optional.of(exact) : Optional.ofNullable(KeyMapping.get(PREFIX + id));
	}

	public static KeyMapping require(String id) {
		return find(id).orElseThrow(() -> new IllegalArgumentException("Unknown key mapping: " + id));
	}

	public static String shortId(KeyMapping mapping) {
		String name = mapping.getName();
		return name.startsWith(PREFIX) ? name.substring(PREFIX.length()) : name;
	}

	public static InputConstants.Key boundKey(KeyMapping mapping) {
		return ((KeyMappingAccessor) mapping).opencrafter$getKey();
	}

	public static boolean isKeyboardBound(KeyMapping mapping) {
		return !mapping.isUnbound() && boundKey(mapping).getType() == InputConstants.Type.KEYBOARD;
	}

	/** True when the mapping currently toggles on press (e.g. "Sneak: Toggle"). */
	public static boolean isToggleMode(KeyMapping mapping) {
		return mapping instanceof ToggleKeyMapping
			&& ((ToggleKeyMappingAccessor) mapping).opencrafter$needsToggle().getAsBoolean();
	}

	/** Registers one click on this mapping only, like {@code KeyMapping.click} does per bound key. */
	public static void click(KeyMapping mapping) {
		KeyMappingAccessor accessor = (KeyMappingAccessor) mapping;
		accessor.opencrafter$setClickCount(accessor.opencrafter$getClickCount() + 1);
	}
}
