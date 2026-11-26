package mod.kelvinlby.opencrafter.client.config;

import dev.isxander.yacl3.api.ButtonOption;
import dev.isxander.yacl3.api.ConfigCategory;
import dev.isxander.yacl3.api.OptionGroup;
import mod.kelvinlby.opencrafter.core.config.TransportConfig;
import mod.kelvinlby.opencrafter.transport.TransportModule;
import net.minecraft.client.Minecraft;

/** Where backends connect. Saving a change reopens the transport, so connected backends reconnect. */
final class TransportTab {
	private static final TransportConfig DEFAULTS = new TransportConfig();

	private TransportTab() {
	}

	static ConfigCategory build(TransportConfig draft, TransportModule transport) {
		return ConfigCategory.createBuilder()
			.name(Texts.name("transport"))
			.tooltip(Texts.description("transport"))
			.group(channel(draft))
			.group(token(draft, transport))
			.group(StatusLabels.group("transport.status", transport.describe()))
			.build();
	}

	private static OptionGroup channel(TransportConfig draft) {
		return OptionGroup.createBuilder()
			.name(Texts.name("transport.channel"))
			.description(Options.description("transport.channel"))
			.option(Options.choice("transport.listen", TransportConfig.Listen.class, DEFAULTS.listen, () -> draft.listen, value -> draft.listen = value))
			.option(Options.text("transport.socket_path", DEFAULTS.socketPath, () -> draft.socketPath, value -> draft.socketPath = value.strip()))
			.option(Options.intField("transport.tcp_port", DEFAULTS.tcpPort, 0, 65535, () -> draft.tcpPort, value -> draft.tcpPort = value))
			.option(Options.text("transport.shm_path", DEFAULTS.sharedMemoryPath, () -> draft.sharedMemoryPath, value -> draft.sharedMemoryPath = value.strip()))
			.option(Options.intField("transport.ring_slots", DEFAULTS.ringSlots, 2, 16, () -> draft.ringSlots, value -> draft.ringSlots = value))
			.build();
	}

	private static OptionGroup token(TransportConfig draft, TransportModule transport) {
		return OptionGroup.createBuilder()
			.name(Texts.name("transport.auth"))
			.description(Options.description("transport.auth"))
			.option(Options.text("transport.token", DEFAULTS.token, () -> draft.token, value -> draft.token = value.strip()))
			.option(ButtonOption.createBuilder()
				.name(Texts.name("transport.copy_token"))
				.description(Options.description("transport.copy_token"))
				.available(transport.token() != null)
				.action((screen, button) -> Minecraft.getInstance().keyboardHandler.setClipboard(transport.token()))
				.build())
			.build();
	}
}
