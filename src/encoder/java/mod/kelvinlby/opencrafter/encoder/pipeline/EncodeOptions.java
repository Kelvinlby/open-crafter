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
 * --profile DIR     game profile each worker copies (default: the dev client's run/, from the launch spec)
 * --display MODE    offscreen (GPU, no window; default), xvfb (software), native (visible windows)
 * --retries N       extra attempts for a recording whose worker failed (default 1)
 * --stall-minutes M kill a worker that made no progress for this long (default 10)
 * --force           redo recordings whose episode already exists
 * --reserve-gb G    keep this much of the output disk free; recordings whose estimated episode does
 *                   not fit wait, and when none fits the run pauses (default 20)
 * --resume-command  the command that started this run, written into the pause marker
 * </pre>
 */
public record EncodeOptions(Path launch, List<Path> inputs, Path output, EncodeConfig config, int workers, Path profile,
							Display display, int retries, int stallMinutes, boolean force, long reserveBytes, String resumeCommand) {
	private static final long GB = 1L << 30;

	public enum Display { OFFSCREEN, XVFB, NATIVE }

	public static EncodeOptions parse(String[] argv) throws IOException {
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
		long reserveGb = 20;
		String resumeCommand = "";
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
				case "--reserve-gb" -> reserveGb = Long.parseLong(value);
				case "--resume-command" -> resumeCommand = value;
				default -> throw new IllegalArgumentException("Unknown option " + key);
			}
		}
		if (launch == null || output == null || inputs.isEmpty()) {
			throw new IllegalArgumentException("Need --launch, --input and --output");
		}
		return new EncodeOptions(launch, List.copyOf(inputs), output, config, Math.max(1, workers),
			profile, display, Math.max(0, retries), Math.max(1, stall), force, Math.max(0, reserveGb) * GB, resumeCommand);
	}

	/** The same options with {@code profile} filled in if none was given. */
	public EncodeOptions withDefaultProfile(Path defaultProfile) {
		return profile != null ? this : new EncodeOptions(launch, inputs, output, config, workers, defaultProfile, display, retries, stallMinutes, force, reserveBytes, resumeCommand);
	}

	public EncodeOptions withConfig(EncodeConfig resolved) {
		return new EncodeOptions(launch, inputs, output, resolved, workers, profile, display, retries, stallMinutes, force, reserveBytes, resumeCommand);
	}

	/** Each worker is a whole game (several busy threads): about one per three cores, at most four. */
	private static int defaultWorkers() {
		return Math.clamp(Runtime.getRuntime().availableProcessors() / 3, 1, 4);
	}
}
