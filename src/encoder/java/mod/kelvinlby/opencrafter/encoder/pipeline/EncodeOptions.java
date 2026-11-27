package mod.kelvinlby.opencrafter.encoder.pipeline;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Command line of the encoder.
 *
 * <pre>
 * --input PATH      a recording (.tar, .mcrec, session folder) or a folder searched recursively
 * --output DIR      one episode folder per recording is written here
 * --launch FILE     worker launch spec (written by the encoderWorkerLaunch task)
 * --config FILE     capture settings (JSON, see EncodeConfig); defaults otherwise
 * --workers N       parallel worker clients (default: by CPU count, at most 4)
 * --profile DIR     game profile each worker copies (default: the dev client's run/)
 * --display MODE    offscreen (GPU, no window; default), xvfb (software), native (visible windows)
 * --retries N       extra attempts for a recording whose worker failed (default 1)
 * --stall-minutes M kill a worker that made no progress for this long (default 10)
 * --force           redo recordings whose episode already exists
 * </pre>
 */
public record EncodeOptions(Path launch, List<Path> inputs, Path output, EncodeConfig config, int workers, Path profile,
							Display display, int retries, int stallMinutes, boolean force) {
	public enum Display { OFFSCREEN, XVFB, NATIVE }

	public static EncodeOptions parse(String[] argv, Path defaultProfile) throws IOException {
		Path launch = null;
		Path output = null;
		Path profile = null;
		EncodeConfig config = new EncodeConfig();
		List<Path> inputs = new ArrayList<>();
		int workers = defaultWorkers();
		Display display = Display.OFFSCREEN;
		int retries = 1;
		int stall = 10;
		boolean force = false;
		for (int i = 0; i < argv.length; i++) {
			String arg = argv[i];
			String key = arg.contains("=") ? arg.substring(0, arg.indexOf('=')) : arg;
			String value = arg.contains("=") ? arg.substring(arg.indexOf('=') + 1) : null;
			if (value == null && !key.equals("--force")) {
				if (i + 1 >= argv.length) {
					throw new IllegalArgumentException("Missing value for " + key);
				}
				value = argv[++i];
			}
			switch (key) {
				case "--launch" -> launch = Path.of(value);
				case "--input" -> inputs.add(Path.of(value));
				case "--output" -> output = Path.of(value);
				case "--config" -> config = EncodeConfig.load(Path.of(value));
				case "--workers" -> workers = Integer.parseInt(value);
				case "--profile" -> profile = Path.of(value);
				case "--display" -> display = Display.valueOf(value.toUpperCase(Locale.ROOT));
				case "--retries" -> retries = Integer.parseInt(value);
				case "--stall-minutes" -> stall = Integer.parseInt(value);
				case "--force" -> force = true;
				default -> throw new IllegalArgumentException("Unknown option " + key);
			}
		}
		if (launch == null || output == null || inputs.isEmpty()) {
			throw new IllegalArgumentException("Need --launch, --input and --output");
		}
		return new EncodeOptions(launch, List.copyOf(inputs), output, config, Math.max(1, workers),
			profile == null ? defaultProfile : profile, display, Math.max(0, retries), Math.max(1, stall), force);
	}

	/** Each worker is a whole game (several busy threads): about one per three cores, at most four. */
	private static int defaultWorkers() {
		return Math.clamp(Runtime.getRuntime().availableProcessors() / 3, 1, 4);
	}
}
