package mod.kelvinlby.opencrafter.encoder.output;

import mod.kelvinlby.opencrafter.encoder.pipeline.EncodeConfig;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/** Pipes raw RGBA frames into an ffmpeg process that encodes them with the configured codec. */
public final class RgbVideoWriter implements AutoCloseable {
	private final Process ffmpeg;
	private final OutputStream stdin;
	private final Path log;
	private final byte[] row;
	private long frames;

	public RgbVideoWriter(Path episodeDir, EncodeConfig config) throws IOException {
		this.log = episodeDir.resolve("ffmpeg.log");
		this.row = new byte[config.width * 4];
		ProcessBuilder builder = new ProcessBuilder(command(episodeDir.resolve(config.rgb.file), config));
		builder.redirectErrorStream(true).redirectOutput(log.toFile());
		this.ffmpeg = builder.start();
		this.stdin = new BufferedOutputStream(ffmpeg.getOutputStream(), 1 << 20);
	}

	/** {@code rgba}: width × height × 4 bytes, top row first. */
	public void write(ByteBuffer rgba) throws IOException {
		ByteBuffer frame = rgba.duplicate();
		while (frame.remaining() >= row.length) {
			frame.get(row);
			stdin.write(row);
		}
		frames++;
	}

	public long frames() {
		return frames;
	}

	@Override
	public void close() throws IOException {
		stdin.close();
		try {
			if (!ffmpeg.waitFor(10, TimeUnit.MINUTES)) {
				ffmpeg.destroyForcibly();
				throw new IOException("ffmpeg did not finish; see " + log);
			}
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IOException("Interrupted waiting for ffmpeg", e);
		}
		if (ffmpeg.exitValue() != 0) {
			throw new IOException("ffmpeg failed with exit code " + ffmpeg.exitValue() + "; see " + log);
		}
	}

	static List<String> command(Path output, EncodeConfig config) {
		EncodeConfig.Rgb rgb = config.rgb;
		List<String> command = new ArrayList<>(List.of(
			"ffmpeg", "-hide_banner", "-nostdin", "-y",
			"-f", "rawvideo", "-pix_fmt", "rgba", "-s", config.width + "x" + config.height,
			"-framerate", String.format(Locale.ROOT, "%.6f", config.fps), "-i", "pipe:0",
			"-c:v", rgb.encoder, "-pix_fmt", rgb.pixelFormat));
		command.addAll(qualityArgs(rgb));
		if (!rgb.preset.isEmpty()) {
			command.addAll(List.of("-preset", rgb.preset));
		}
		// RGB input: tag the YUV conversion so players and decoders agree on the colours.
		command.addAll(List.of("-colorspace", "bt709", "-color_primaries", "bt709", "-color_trc", "bt709", "-color_range", "pc"));
		command.addAll(rgb.extraArgs);
		command.add(output.toString());
		return command;
	}

	private static List<String> qualityArgs(EncodeConfig.Rgb rgb) {
		return switch (rgb.encoder) {
			case "ffv1" -> List.of("-level", "3", "-slices", "4", "-slicecrc", "1");
			case "av1_nvenc", "hevc_nvenc", "h264_nvenc" -> List.of("-rc", "vbr", "-cq", Integer.toString(rgb.quality), "-b:v", "0");
			case "libaom-av1" -> List.of("-crf", Integer.toString(rgb.quality), "-b:v", "0", "-row-mt", "1");
			default -> List.of("-crf", Integer.toString(rgb.quality));
		};
	}
}
