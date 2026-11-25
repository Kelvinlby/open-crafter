package mod.kelvinlby.opencrafter.client.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import mod.kelvinlby.opencrafter.client.vision.RateModes;
import mod.kelvinlby.opencrafter.client.vision.VisionControl;
import mod.kelvinlby.opencrafter.client.vision.VisionSettings;
import mod.kelvinlby.opencrafter.core.config.VisionConfig.RateMode;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.network.chat.Component;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;

/**
 * {@code /opencrafter vision rate <tick|fps|hz> [n]} and {@code /opencrafter vision status}.
 * Other modules may add their own children under {@code /opencrafter}; brigadier merges them.
 */
public final class VisionCommand {
	private final VisionControl control;

	private VisionCommand(VisionControl control) {
		this.control = control;
	}

	public static void register(VisionControl control) {
		VisionCommand command = new VisionCommand(control);
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, context) -> command.registerInto(dispatcher));
	}

	private void registerInto(CommandDispatcher<FabricClientCommandSource> dispatcher) {
		dispatcher.register(literal("opencrafter").then(literal("vision")
			.then(rateNode())
			.then(literal("status").executes(this::status))));
	}

	private LiteralArgumentBuilder<FabricClientCommandSource> rateNode() {
		return literal("rate")
			.then(literal("tick").executes(ctx -> setRate(ctx, RateMode.TICK, control.settings().fixedHz())))
			.then(literal("fps").executes(ctx -> setRate(ctx, RateMode.MAX_FPS, control.settings().fixedHz())))
			.then(literal("hz")
				.executes(ctx -> setRate(ctx, RateMode.FIXED_HZ, control.settings().fixedHz()))
				.then(argument("n", DoubleArgumentType.doubleArg(VisionSettings.MIN_HZ, VisionSettings.MAX_HZ))
					.executes(ctx -> setRate(ctx, RateMode.FIXED_HZ, DoubleArgumentType.getDouble(ctx, "n")))));
	}

	private int setRate(CommandContext<FabricClientCommandSource> ctx, RateMode mode, double hz) {
		FabricClientCommandSource source = ctx.getSource();
		CompletableFuture.runAsync(() -> control.setRate(mode, hz))
			.whenComplete((ignored, error) -> source.getClient().execute(() -> reportRate(source, mode, hz, error)));
		return 1;
	}

	private static void reportRate(FabricClientCommandSource source, RateMode mode, double hz, Throwable error) {
		if (error != null) {
			source.sendError(Component.literal("Could not set vision rate: " + error.getMessage()));
		} else {
			String detail = mode == RateMode.FIXED_HZ ? String.format(Locale.ROOT, " (%.2f Hz)", hz) : "";
			source.sendFeedback(Component.literal("Vision rate: " + RateModes.wireName(mode) + detail));
		}
	}

	private int status(CommandContext<FabricClientCommandSource> ctx) {
		Map<String, Object> status = control.status();
		StringBuilder text = new StringBuilder("Vision:");
		status.forEach((key, value) -> text.append("\n  ").append(key).append(" = ").append(value));
		ctx.getSource().sendFeedback(Component.literal(text.toString()));
		return 1;
	}
}
