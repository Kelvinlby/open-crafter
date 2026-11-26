package mod.kelvinlby.opencrafter.client.config;

import mod.kelvinlby.opencrafter.OpenCrafter;
import net.minecraft.network.chat.Component;

import java.util.Locale;

/** Translation keys of the settings screen: {@code open-crafter.config.<path>} and {@code <path>.desc}. */
final class Texts {
	private static final String PREFIX = OpenCrafter.MOD_ID + ".config.";

	private Texts() {
	}

	static Component name(String path) {
		return Component.translatable(PREFIX + path);
	}

	static Component description(String path) {
		return Component.translatable(PREFIX + path + ".desc");
	}

	static Component enumValue(Enum<?> value) {
		String type = value.getDeclaringClass().getSimpleName().toLowerCase(Locale.ROOT);
		return Component.translatable(PREFIX + "enum." + type + "." + value.name().toLowerCase(Locale.ROOT));
	}
}
