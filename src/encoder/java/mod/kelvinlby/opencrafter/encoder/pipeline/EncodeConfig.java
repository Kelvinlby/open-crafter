package mod.kelvinlby.opencrafter.encoder.pipeline;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * What to capture and how to store it. Loaded from a JSON file (any key left out keeps its
 * default); every worker gets the same copy inside its job.
 */
public final class EncodeConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

	/** Captured frames per second of replay time. Ticks stay at the recorded rate whatever this is. */
	public double fps = 20.0;
	/**
	 * Where in the tick each frame falls when {@link #fps} is a multiple of 20 (0 = right after the
	 * tick, i.e. the previous tick's state; 0.999 = the tick's own state). Other rates sweep through.
	 */
	public double tickPhase = 0.5;
	/** Framebuffer size in pixels. */
	public int width = 854;
	public int height = 480;
	/** Vision streams: {@code rgb}, {@code flow}, {@code flow_occluded}. */
	public List<String> streams = new ArrayList<>(List.of("rgb"));
	public Rgb rgb = new Rgb();
	public Flow flow = new Flow();
	/** Render a frame again (without advancing time) until the visible chunks are meshed, at most this often. 0 = off. */
	public int chunkSettleFrames = 30;
	/** Skip this much replay time after the player has loaded before capturing (seconds). */
	public double startSeconds = 0.0;
	/** Stop after this much captured replay time (seconds); 0 = the whole recording. */
	public double maxSeconds = 0.0;
	/**
	 * Server packets reach the replayed client this long after the server sent them (the round trip
	 * the recorded client had). -1 = the ping stored in the recording, or 0 if it has none.
	 */
	public int s2cDelayMillis = -1;
	/** Let the recorded server push its resource packs (downloads them). */
	public boolean serverResourcePacks = false;

	public static final class Rgb {
		/** ffmpeg video encoder: {@code libsvtav1}, {@code av1_nvenc}, {@code libaom-av1}, {@code libx265}, {@code hevc_nvenc}, {@code ffv1}, ... */
		public String encoder = "libsvtav1";
		/** Constant-quality value (CRF / CQ); ignored by lossless encoders. */
		public int quality = 20;
		/** Encoder speed preset (SVT-AV1 0-13, x265 names, NVENC p1-p7); empty = encoder default. */
		public String preset = "6";
		/** Pixel format handed to the encoder, e.g. {@code yuv420p10le}, {@code yuv444p}, {@code gbrp}. */
		public String pixelFormat = "yuv420p10le";
		/** Container file name (extension picks the muxer). */
		public String file = "rgb.mkv";
		/** Extra ffmpeg output arguments, appended verbatim. */
		public List<String> extraArgs = new ArrayList<>();
	}

	public static final class Flow {
		/** {@code f16} (half floats), {@code f32} (exact) or {@code q16} (int16 fixed point, 1/{@link #q16Scale} px). */
		public String precision = "f16";
		public double q16Scale = 16.0;
		public int zstdLevel = 3;
	}

	public static EncodeConfig load(Path file) throws IOException {
		EncodeConfig config = GSON.fromJson(Files.readString(file), EncodeConfig.class);
		return config == null ? new EncodeConfig() : config;
	}

	public String toJson() {
		return GSON.toJson(this);
	}

	public static EncodeConfig fromJson(String json) {
		return GSON.fromJson(json, EncodeConfig.class);
	}

	public boolean wants(String stream) {
		return streams.contains(stream);
	}

	public long frameNanos() {
		return Math.round(1e9 / fps);
	}
}
