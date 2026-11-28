package mod.kelvinlby.opencrafter.encoder.pipeline;

import mod.kelvinlby.opencrafter.encoder.format.Recording;

import java.io.IOException;

/**
 * How much disk an episode will take, before it is encoded: the captured replay time times a byte
 * rate. The rate starts from a deliberately high guess per stream and pixel and is then replaced by
 * the highest rate measured on finished episodes (plus a margin).
 */
final class SizeEstimate {
	/** Upper-end bytes per pixel and frame, measured at 854×480: RGB (HEVC 4:4:4, QP 8), f16 flow, occlusion bits. */
	private static final double RGB_BYTES_PER_PIXEL = 0.35;
	private static final double FLOW_BYTES_PER_PIXEL = 0.65;
	private static final double OCCLUSION_BYTES_PER_PIXEL = 0.02;
	/** Metadata (frames.jsonl.zst) and container overhead per frame. */
	private static final double FRAME_OVERHEAD_BYTES = 2_000;
	/** Recorded bytes per second of a session at its least dense (only used when a recording does not say how long it is). */
	private static final double MIN_RECORDING_BYTES_PER_SECOND = 10_000;
	/** Headroom on the measured rate: one episode can be busier than every one before it. */
	static final double MEASURED_MARGIN = 1.15;

	private SizeEstimate() {
	}

	/** Seconds of the recording the episode will cover (respecting {@code startSeconds} / {@code maxSeconds}). */
	static double capturedSeconds(Recording recording, EncodeConfig config) throws IOException {
		double seconds = recording.durationMillis().isPresent()
			? recording.durationMillis().getAsLong() / 1000.0
			: recording.storedBytes() / MIN_RECORDING_BYTES_PER_SECOND;
		return capturedSeconds(seconds, config);
	}

	static double capturedSeconds(double recordingSeconds, EncodeConfig config) {
		double seconds = Math.max(0, recordingSeconds - config.startSeconds);
		return config.maxSeconds > 0 ? Math.min(seconds, config.maxSeconds) : seconds;
	}

	/** The rate to assume before any episode was measured: bytes per second of captured replay time. */
	static double initialBytesPerSecond(EncodeConfig config) {
		double pixels = (double) config.width * config.height;
		double perFrame = FRAME_OVERHEAD_BYTES;
		for (String stream : config.streams) {
			perFrame += pixels * switch (stream) {
				case "rgb" -> RGB_BYTES_PER_PIXEL;
				case "flow" -> FLOW_BYTES_PER_PIXEL;
				case "flow_occluded" -> OCCLUSION_BYTES_PER_PIXEL;
				default -> FLOW_BYTES_PER_PIXEL;
			};
		}
		return perFrame * config.fps;
	}

	static long bytes(double capturedSeconds, double bytesPerSecond) {
		return (long) Math.ceil(capturedSeconds * bytesPerSecond);
	}
}
