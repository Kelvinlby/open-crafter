package mod.kelvinlby.opencrafter.encoder.client;

import mod.kelvinlby.opencrafter.api.Args;
import mod.kelvinlby.opencrafter.api.ModContext;
import mod.kelvinlby.opencrafter.api.handler.HandlerRegistry;
import mod.kelvinlby.opencrafter.encoder.output.EpisodeWriter;
import mod.kelvinlby.opencrafter.encoder.pipeline.Job;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/** The encoder's use of open-crafter's vision sensor, through its public ops. */
final class EncoderCapture {
	private EncoderCapture() {
	}

	static EpisodeWriter openEpisode(Job job) {
		try {
			return new EpisodeWriter(job.outputPath(), job.config());
		} catch (IOException e) {
			throw new ReplayException("Could not create " + job.output(), e);
		}
	}

	/** Every rendered frame the encoder marks as captured is captured ({@code max_fps}); the encoder paces them. */
	static void configureVision(List<String> streams) {
		call("vision.configure", Map.of("streams", streams, "rate_mode", "max_fps"));
	}

	static boolean allCapturedFramesPublished() {
		Map<?, ?> status = (Map<?, ?>) call("vision.status", Map.of());
		long captured = ((Number) status.get("frames_captured")).longValue();
		long published = ((Number) status.get("frames_published")).longValue();
		return published >= captured;
	}

	private static Object call(String op, Map<String, Object> args) {
		ModContext context = EncoderModule.context();
		if (context == null) {
			throw new ReplayException("open-crafter did not load the encoder module", null);
		}
		HandlerRegistry.Entry entry = context.handlers().find(op).orElseThrow(() -> new ReplayException("open-crafter has no op " + op, null));
		try {
			return entry.handler().handle(new Args(args));
		} catch (Exception e) {
			throw new ReplayException(op + " failed", e);
		}
	}
}
