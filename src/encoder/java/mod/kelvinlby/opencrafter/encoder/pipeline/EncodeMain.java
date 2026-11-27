package mod.kelvinlby.opencrafter.encoder.pipeline;

import mod.kelvinlby.opencrafter.encoder.EncoderLog;
import mod.kelvinlby.opencrafter.encoder.format.McrecHeader;
import mod.kelvinlby.opencrafter.encoder.format.Recording;
import mod.kelvinlby.opencrafter.encoder.format.RecordingReader;
import mod.kelvinlby.opencrafter.encoder.translate.ProtocolNormalizer;
import mod.kelvinlby.opencrafter.encoder.translate.ViaRuntime;
import net.minecraft.SharedConstants;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The encoder ({@code ./gradlew encode}): finds recordings, brings each into the native protocol
 * (Via, in this process, in parallel), and replays them in parallel headless worker clients that
 * write one episode folder each. Recordings whose episode already exists are skipped, so a run can
 * be repeated to pick up new recordings or retry failed ones.
 */
public final class EncodeMain {
	private static final String WORK_DIR = ".encoder";

	private EncodeMain() {
	}

	public static void main(String[] args) throws Exception {
		WorkerLaunch launch = null;
		EncodeOptions options;
		try {
			options = EncodeOptions.parse(args, Path.of("run"));
			launch = WorkerLaunch.read(options.launch());
		} catch (IllegalArgumentException e) {
			System.err.println(e.getMessage());
			System.exit(2);
			return;
		}
		EncodeOptions effective = launch.profileDir() == null ? options : withProfileDefault(options, launch);
		System.exit(run(effective, launch) ? 0 : 1);
	}

	private static EncodeOptions withProfileDefault(EncodeOptions options, WorkerLaunch launch) {
		if (!options.profile().equals(Path.of("run"))) {
			return options;
		}
		return new EncodeOptions(options.launch(), options.inputs(), options.output(), options.config(), options.workers(),
			Path.of(launch.profileDir()), options.display(), options.retries(), options.stallMinutes(), options.force());
	}

	private static boolean run(EncodeOptions options, WorkerLaunch launch) throws IOException, InterruptedException {
		SharedConstants.tryDetectVersion();
		Path output = options.output().toAbsolutePath();
		Path work = output.resolve(WORK_DIR);
		Files.createDirectories(work);
		ViaRuntime.ensureStarted(work.resolve("via"));
		List<Path> recordings = RecordingScanner.find(options.inputs());
		RunReport report = new RunReport();
		report.planned(recordings.size());
		EncoderLog.LOGGER.info("{} recordings, {} workers ({}), profile {}", recordings.size(), options.workers(),
			options.display().name().toLowerCase(java.util.Locale.ROOT), options.profile());

		BlockingQueue<Job> ready = new LinkedBlockingQueue<>();
		AtomicInteger preparing = new AtomicInteger(recordings.size());
		ExecutorService translators = Executors.newFixedThreadPool(Math.max(1, Runtime.getRuntime().availableProcessors() / 4));
		Set<String> names = new HashSet<>();
		for (Path source : recordings) {
			String name = uniqueName(names, Recording.open(source).baseName());
			translators.submit(() -> {
				try {
					prepare(source, name, output, work, options, report).ifPresent(ready::add);
				} finally {
					preparing.decrementAndGet();
				}
			});
		}
		translators.shutdown();
		new WorkerPool(options, launch, work, report).run(ready, () -> preparing.get() > 0);
		translators.awaitTermination(1, TimeUnit.MINUTES);
		report.write(output);
		return !report.anyFailed();
	}

	/** The job for one recording, translating it first if it was recorded with another protocol. */
	private static java.util.Optional<Job> prepare(Path source, String name, Path output, Path work, EncodeOptions options, RunReport report) {
		Path episode = output.resolve(name);
		if (!options.force() && Files.isRegularFile(episode.resolve("episode.json"))) {
			report.skipped(name);
			return java.util.Optional.empty();
		}
		try {
			Path playable = nativeRecording(Recording.open(source), work.resolve("native").resolve(name + ".mcrec"));
			return java.util.Optional.of(new Job(name, playable.toString(), source.toAbsolutePath().toString(), episode.toString(), options.config()));
		} catch (Exception e) {
			report.failed(name, source.toString(), "could not prepare: " + e.getMessage());
			return java.util.Optional.empty();
		}
	}

	private static Path nativeRecording(Recording recording, Path translated) throws IOException {
		int nativeProtocol = SharedConstants.getProtocolVersion();
		McrecHeader header;
		try (RecordingReader reader = recording.reader()) {
			header = reader.header();
		}
		if (!ProtocolNormalizer.needsTranslation(header, nativeProtocol)) {
			return recording.path();
		}
		if (Files.isRegularFile(translated) && Files.getLastModifiedTime(translated).compareTo(Files.getLastModifiedTime(recording.path())) > 0) {
			return translated;
		}
		long started = System.nanoTime();
		ProtocolNormalizer.Result result = ProtocolNormalizer.normalize(recording, translated, nativeProtocol, SharedConstants.getCurrentVersion().name());
		EncoderLog.LOGGER.info("Translated {} from {} ({} packets, {} untranslatable) in {} s", recording.baseName(), header.minecraftVersion(),
			result.packetsIn(), result.failed(), Math.round((System.nanoTime() - started) / 1e9));
		result.warnings().forEach(w -> EncoderLog.LOGGER.warn("  {}: {}", recording.baseName(), w));
		return translated;
	}

	private static synchronized String uniqueName(Set<String> taken, String base) {
		String name = base;
		for (int i = 2; !taken.add(name); i++) {
			name = base + "-" + i;
		}
		return name;
	}
}
