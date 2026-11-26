package mod.kelvinlby.opencrafter.client.config;

import dev.isxander.yacl3.api.LabelOption;
import dev.isxander.yacl3.api.OptionGroup;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

import java.util.Map;

/** A read-only group of {@code key = value} lines, captured when the screen opens. */
final class StatusLabels {
	private StatusLabels() {
	}

	static OptionGroup group(String path, Map<String, Object> status) {
		OptionGroup.Builder group = OptionGroup.createBuilder()
			.name(Texts.name(path))
			.description(Options.description(path))
			.collapsed(true);
		status.forEach((key, value) -> group.option(LabelOption.create(line(key, value))));
		return group.build();
	}

	private static Component line(String key, Object value) {
		return Component.literal(key + " = ").withStyle(ChatFormatting.GRAY).append(Component.literal(String.valueOf(value)).withStyle(ChatFormatting.WHITE));
	}
}
