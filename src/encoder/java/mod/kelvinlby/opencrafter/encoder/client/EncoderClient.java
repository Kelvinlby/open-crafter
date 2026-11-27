package mod.kelvinlby.opencrafter.encoder.client;

import mod.kelvinlby.opencrafter.encoder.EncoderLog;
import mod.kelvinlby.opencrafter.encoder.pipeline.Job;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Entrypoint of a worker client. Inert unless the JVM was started with {@value Job#PROPERTY}: then
 * it waits for the title screen, takes over the game clock and replays that job's recording.
 */
public final class EncoderClient implements ClientModInitializer {
	private static final VirtualClock CLOCK = new VirtualClock();
	private @Nullable Job job;

	@Override
	public void onInitializeClient() {
		String jobFile = System.getProperty(Job.PROPERTY);
		if (jobFile == null) {
			return;
		}
		try {
			job = Job.read(Path.of(jobFile));
		} catch (IOException e) {
			ReplayHooks.fail(e);
			return;
		}
		ClientTickEvents.END_CLIENT_TICK.register(this::startOnTitleScreen);
		EncoderLog.LOGGER.info("Encoder worker for {}", job.name());
	}

	private void startOnTitleScreen(Minecraft minecraft) {
		Job pending = job;
		if (pending == null || !(minecraft.gui.screen() instanceof TitleScreen) || minecraft.gui.overlay() != null) {
			return;
		}
		job = null;
		WorkerOptions.apply(minecraft, pending.config());
		CLOCK.install();
		try {
			ReplaySession.start(pending, minecraft, CLOCK);
		} catch (IOException | RuntimeException e) {
			ReplayHooks.fail(e);
		}
	}
}
