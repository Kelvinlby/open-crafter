package mod.kelvinlby.opencrafter.client.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import mod.kelvinlby.opencrafter.core.config.ConfigStore;
import mod.kelvinlby.opencrafter.core.config.TransportConfig;
import mod.kelvinlby.opencrafter.transport.TransportModule;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.network.chat.Component;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;

/**
 * {@code /opencrafter transport ...}: shows where backends connect and overrides the
 * automatic choices. Every change is persisted and applied at once (backends reconnect).
 */
public final class TransportCommand {
	private static final String DEFAULT = "default";

	private final TransportModule transport;
	private final ConfigStore config;

	private TransportCommand(TransportModule transport, ConfigStore config) {
		this.transport = transport;
		this.config = config;
	}

	public static void register(TransportModule transport, ConfigStore config) {
		TransportCommand command = new TransportCommand(transport, config);
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, context) -> command.registerInto(dispatcher));
	}

	private void registerInto(CommandDispatcher<FabricClientCommandSource> dispatcher) {
		dispatcher.register(literal("opencrafter").then(literal("transport")
			.then(literal("status").executes(this::status))
			.then(literal("restart").executes(ctx -> apply(ctx, settings -> {
			})))
			.then(listenNode())
			.then(pathNode("socket", (settings, path) -> settings.socketPath = path))
			.then(pathNode("shm", (settings, path) -> settings.sharedMemoryPath = path))
			.then(portNode())
			.then(tokenNode())));
	}

	private LiteralArgumentBuilder<FabricClientCommandSource> listenNode() {
		LiteralArgumentBuilder<FabricClientCommandSource> node = literal("listen");
		for (TransportConfig.Listen mode : TransportConfig.Listen.values()) {
			node.then(literal(mode.name().toLowerCase(Locale.ROOT)).executes(ctx -> apply(ctx, settings -> settings.listen = mode)));
		}
		return node;
	}

	/** {@code <name> default} restores the automatic path, {@code <name> <path>} sets one. */
	private LiteralArgumentBuilder<FabricClientCommandSource> pathNode(String name, PathSetter setter) {
		return literal(name)
			.then(literal(DEFAULT).executes(ctx -> apply(ctx, settings -> setter.set(settings, ""))))
			.then(argument("path", StringArgumentType.greedyString())
				.executes(ctx -> apply(ctx, settings -> setter.set(settings, StringArgumentType.getString(ctx, "path")))));
	}

	private LiteralArgumentBuilder<FabricClientCommandSource> portNode() {
		return literal("port")
			.then(literal(DEFAULT).executes(ctx -> apply(ctx, settings -> settings.tcpPort = 0)))
			.then(argument("port", IntegerArgumentType.integer(0, 65535))
				.executes(ctx -> apply(ctx, settings -> settings.tcpPort = IntegerArgumentType.getInteger(ctx, "port"))));
	}

	/** The token is never printed (chat is part of the vision stream); {@code copy} puts it on the clipboard. */
	private LiteralArgumentBuilder<FabricClientCommandSource> tokenNode() {
		return literal("token")
			.then(literal("random").executes(ctx -> apply(ctx, settings -> settings.token = "")))
			.then(literal("copy").executes(this::copyToken))
			.then(literal("set").then(argument("token", StringArgumentType.greedyString())
				.executes(ctx -> apply(ctx, settings -> settings.token = StringArgumentType.getString(ctx, "token")))));
	}

	private int status(CommandContext<FabricClientCommandSource> ctx) {
		ctx.getSource().sendFeedback(Component.literal(statusText()));
		return 1;
	}

	private int copyToken(CommandContext<FabricClientCommandSource> ctx) {
		String token = transport.token();
		if (token == null) {
			ctx.getSource().sendError(Component.literal("The Unix socket needs no token (listen tcp to use one)"));
			return 0;
		}
		ctx.getSource().getClient().keyboardHandler.setClipboard(token);
		ctx.getSource().sendFeedback(Component.literal("Token copied to the clipboard"));
		return 1;
	}

	/** Persists the change, then reopens the transport off the main thread (it binds sockets and maps files). */
	private int apply(CommandContext<FabricClientCommandSource> ctx, Consumer<TransportConfig> change) {
		FabricClientCommandSource source = ctx.getSource();
		CompletableFuture.runAsync(() -> {
			config.update(all -> change.accept(all.transport));
			transport.reconfigure(config.get().transport.copy());
		}).whenComplete((ignored, error) -> source.getClient().execute(() -> report(source, error)));
		return 1;
	}

	private void report(FabricClientCommandSource source, Throwable error) {
		if (error != null) {
			source.sendError(Component.literal("Could not apply transport settings: " + error.getMessage()));
		} else {
			source.sendFeedback(Component.literal(statusText()));
		}
	}

	private String statusText() {
		TransportConfig settings = config.get().transport;
		StringBuilder text = new StringBuilder("Transport:");
		Map<String, Object> state = transport.describe();
		state.forEach((key, value) -> text.append("\n  ").append(key).append(" = ").append(value));
		text.append("\nSettings: listen ").append(settings.listen.name().toLowerCase(Locale.ROOT))
			.append(", socket ").append(orDefault(settings.socketPath))
			.append(", port ").append(settings.tcpPort == 0 ? DEFAULT : settings.tcpPort)
			.append(", shm ").append(orDefault(settings.sharedMemoryPath))
			.append(", token ").append(settings.token.isBlank() ? "random" : "fixed");
		return text.toString();
	}

	private static String orDefault(String value) {
		return value == null || value.isBlank() ? DEFAULT : value;
	}

	@FunctionalInterface
	private interface PathSetter {
		void set(TransportConfig settings, String path);
	}
}
