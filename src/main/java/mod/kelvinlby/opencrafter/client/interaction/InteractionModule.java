package mod.kelvinlby.opencrafter.client.interaction;

import mod.kelvinlby.opencrafter.api.ModContext;
import mod.kelvinlby.opencrafter.api.ModModule;
import mod.kelvinlby.opencrafter.client.interaction.capture.FrameClock;
import mod.kelvinlby.opencrafter.client.interaction.capture.InteractionCapture;
import mod.kelvinlby.opencrafter.client.interaction.debug.ModActionDriver;
import mod.kelvinlby.opencrafter.client.interaction.outcome.OutcomeResolver;
import mod.kelvinlby.opencrafter.client.interaction.sensor.FrameInteractions;
import mod.kelvinlby.opencrafter.client.interaction.sensor.InteractionPublisher;
import mod.kelvinlby.opencrafter.core.config.ConfigStore;
import mod.kelvinlby.opencrafter.core.config.InteractionConfig;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.util.Mth;

/**
 * The interaction event layer: every in-world action the local player sends, whoever issued it,
 * with its target, contact point and outcome. Feeds Hand View and the other visuals, and is
 * published to backends as ground-truth action labels (docs/ops/interaction.md).
 */
public final class InteractionModule implements ModModule {
	@Override
	public void install(ModContext context) {
		OutcomeResolver outcomes = new OutcomeResolver(Interactions.BUS, () -> outcomeWindowNanos(context.config()));
		FrameClock clock = new FrameClock();
		InteractionCapture capture = new InteractionCapture(Interactions.BUS, outcomes, Interactions.ROTATION, clock);
		InteractionHooks.install(capture, outcomes);
		installSensor(context, clock);
		ClientTickEvents.END_CLIENT_TICK.register(client -> outcomes.tick());
		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> capture.reset());
		ModActionDriver.installIfRequested();
	}

	private static void installSensor(ModContext context, FrameClock clock) {
		FrameInteractions frames = new FrameInteractions(clock);
		context.annotators().register(frames);
		Interactions.subscribe(frames);
		Interactions.subscribe(new InteractionPublisher(context.events()));
	}

	private static long outcomeWindowNanos(ConfigStore config) {
		int millis = Mth.clamp(config.get().interaction.outcomeWindowMillis,
			InteractionConfig.MIN_OUTCOME_WINDOW_MILLIS, InteractionConfig.MAX_OUTCOME_WINDOW_MILLIS);
		return millis * 1_000_000L;
	}
}
