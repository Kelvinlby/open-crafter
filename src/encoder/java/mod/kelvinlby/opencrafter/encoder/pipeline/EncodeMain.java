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
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The encoder ({@code ./gradlew encode}): finds recordings, brings each into the native protocol
 * (Via, in this process, in parallel), and replays them in parallel headless worker clients that
 * write one episode folder each. Recordings whose episode already exists are skipped, so a run can
 * be repeated to pick up new recordings, retry failed ones or continue one that paused because the
 * output disk was full ({@link PauseMarker}). An episode folder is complete once it has
 * {@code episode.json}; anything less is deleted, never left behind.
 */
public final class EncodeMain {
	private static final String WORK_DIR = ".encoder";

	private EncodeMain() {
	}

	public static void main(String[] args) throws Exception {
		EncodeOptions options;
		WorkerLaunch launch;
		try {
			EncodeOptions parsed = EncodeOptions.parse(args);
			launch = WorkerLaunch.read(parsed.launch());
			options = parsed.withDefaultProfile(Path.of(launch.profileDir()));
		} catch (IllegalArgumentException e) {
			System.err.println(e.getMessage());
			System.exit(2);
			return;
		}
		System.exit(run(options.withConfig(VideoEncoders.resolve(options.config())), launch) ? 0 : 1);
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

		JobScheduler scheduler = new JobScheduler(new OutputDisk(output), options.reserveBytes(),
			SizeEstimate.initialBytesPerSecond(options.config()));
		AtomicInteger preparing = new AtomicInteger(recordings.size());
		ExecutorService translators = Executors.newFixedThreadPool(Math.max(1, Runtime.getRuntime().availableProcessors() / 4));
		Set<String> names = new HashSet<>();
		for (Path source : recordings) {
			Recording recording = Recording.open(source);
			String name = uniqueName(names, recording.baseName());
			translators.submit(() -> {
				try {
					prepare(recording, name, output, work, options, report).ifPresent(scheduler::add);
				} finally {
					if (preparing.decrementAndGet() == 0) {
						scheduler.doneAdding();
					}
				}
			});
		}
		if (recordings.isEmpty()) {
			scheduler.doneAdding();
		}
		translators.shutdown();
		new WorkerPool(options, launch, work, report, scheduler).run();
		translators.awaitTermination(1, TimeUnit.MINUTES);
		reportLeftOver(scheduler, report);
		report.write(output);
		PauseMarker.update(output, report, options.resumeCommand(), scheduler.freeBytes());
		return !report.anyFailed();
	}

	/** Jobs that never started because their episode would not fit: pending for the next run. */
	private static void reportLeftOver(JobScheduler scheduler, RunReport report) {
		for (JobScheduler.Planned planned : scheduler.waiting()) {
			report.pending(planned.job(), "not enough free disk space (needs about "
				+ Math.round(scheduler.estimate(planned) / (double) (1L << 30)) + " GB)");
		}
		if (report.anyPending()) {
			EncoderLog.LOGGER.warn("Encoding PAUSED: the output disk is full; see {}", PauseMarker.FILE);
		}
	}

	/** The job for one recording, translating it first if it was recorded with another protocol. */
	private static Optional<JobScheduler.Planned> prepare(Recording recording, String name, Path output, Path work, EncodeOptions options,
														  RunReport report) {
		Path episode = output.resolve(name);
		String source = recording.path().toAbsolutePath().toString();
		try {
			if (!clearForEncoding(episode, name, options, report)) {
				return Optional.empty();
			}
			double seconds = SizeEstimate.capturedSeconds(recording, options.config());
			Path playable = nativeRecording(recording, work.resolve("native").resolve(name + ".mcrec"));
			Job job = new Job(name, playable.toString(), source, episode.toString(), options.config());
			return Optional.of(new JobScheduler.Planned(job, seconds));
		} catch (Exception e) {
			report.failed(name, source, "could not prepare: " + e.getMessage());
			return Optional.empty();
		}
	}

	/**
	 * Whether the recording still needs encoding; deletes what an interrupted run left of its episode.
	 * A complete episode is skipped (unless forced), a folder that is not an episode is never touched.
	 */
	private static boolean clearForEncoding(Path episode, String name, EncodeOptions options, RunReport report) throws IOException {
		switch (EpisodeFolder.of(episode, options.config())) {
			case COMPLETE -> {
				if (!options.force()) {
					report.skipped(name);
					return false;
				}
			}
			case UNFINISHED -> {
				EncoderLog.LOGGER.info("{}: deleting the unfinished episode an earlier run left", name);
				WorkerProfile.deleteRecursively(episode);
			}
			case FOREIGN -> throw new IOException(episode + " exists and is not an episode folder; not overwriting it");
			case ABSENT -> {
			}
		}
		return true;
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
