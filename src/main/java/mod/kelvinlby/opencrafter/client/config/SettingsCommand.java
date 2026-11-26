package mod.kelvinlby.opencrafter.client.config;

import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;

/** {@code /opencrafter} opens the settings screen. */
public final class SettingsCommand {
	private static boolean openRequested;

	private SettingsCommand() {
	}

	public static void register() {
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, context) -> dispatcher.register(literal("opencrafter").executes(ctx -> {
			openRequested = true;
			return 1;
		})));
		ClientTickEvents.END_CLIENT_TICK.register(SettingsCommand::openIfRequested);
	}

	// The chat screen closes after running the command, so the settings open on the next tick
	private static void openIfRequested(Minecraft client) {
		if (openRequested) {
			openRequested = false;
			client.gui.setScreen(SettingsScreen.create(null));
		}
	}
}
