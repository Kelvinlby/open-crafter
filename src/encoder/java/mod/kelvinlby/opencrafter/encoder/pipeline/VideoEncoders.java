package mod.kelvinlby.opencrafter.encoder.pipeline;

import mod.kelvinlby.opencrafter.encoder.EncoderLog;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** Resolves the {@code auto} RGB encoder once per run, by trying the GPU encoder on a tiny clip. */
final class VideoEncoders {
	private static final String GPU = "hevc_nvenc";
	private static final String CPU = "libx265";

	private VideoEncoders() {
	}

	/** The config with {@code rgb.encoder} resolved, so every worker uses the same encoder. */
	static EncodeConfig resolve(EncodeConfig config) {
		if (!config.rgb.encoder.equals("auto")) {
			return config;
		}
		EncodeConfig resolved = EncodeConfig.fromJson(config.toJson());
		resolved.rgb.encoder = works(GPU, config.rgb.pixelFormat) ? GPU : CPU;
		EncoderLog.LOGGER.info("RGB encoder: {} ({})", resolved.rgb.encoder, resolved.rgb.pixelFormat);
		return resolved;
	}

	private static boolean works(String encoder, String pixelFormat) {
		List<String> probe = List.of("ffmpeg", "-v", "error", "-nostdin", "-f", "lavfi", "-i", "color=c=gray:s=256x256:d=0.1",
			"-c:v", encoder, "-pix_fmt", pixelFormat, "-f", "null", "-");
		try {
			Process process = new ProcessBuilder(probe).redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
			return process.waitFor(30, TimeUnit.SECONDS) && process.exitValue() == 0;
		} catch (IOException e) {
			throw new IllegalStateException("ffmpeg is needed for RGB video and could not be started", e);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			return false;
		}
	}
}
