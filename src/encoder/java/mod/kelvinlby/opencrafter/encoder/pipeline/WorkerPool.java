package mod.kelvinlby.opencrafter.encoder.pipeline;

import mod.kelvinlby.opencrafter.encoder.EncoderLog;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * Runs jobs in worker clients, {@code workers} at a time, as the {@link JobScheduler} lets them
 * start. Each worker slot keeps one game directory (made once from the profile) and starts a fresh
 * client per job, so a crash costs one recording. A worker that makes no progress (no log output,
 * no episode output) for the stall limit is killed; a failed job is retried from scratch.
 *
 * <p>No unfinished episode is left behind: a job that fails for good, or runs out of disk, has its
 * episode folder deleted. A job that ran out of disk is not retried but left pending; when free
 * space drops below half the reserve (estimates were too low), the running job with the most left
 * to write is stopped and left pending, so the others can finish.
 */
final class WorkerPool {
	private static final long POLL_MILLIS = 2_000;
	private static final long SPACE_CHECK_MILLIS = 5_000;
	private static final int LOG_TAIL_BYTES = 64 * 1024;
	private static final String NO_SPACE = "No space left on device";

	private final EncodeOptions options;
	private final WorkerLaunch launch;
	private final Path workDir;
	private final RunReport report;
	private final JobScheduler scheduler;
	private final Map<JobScheduler.Lease, Process> running = new ConcurrentHashMap<>();

	WorkerPool(EncodeOptions options, WorkerLaunch launch, Path workDir, RunReport report, JobScheduler scheduler) {
		this.options = options;
		this.launch = launch;
		this.workDir = workDir;
		this.report = report;
		this.scheduler = scheduler;
		Runtime.getRuntime().addShutdownHook(new Thread(() -> running.values().forEach(Process::destroyForcibly)));
	}

	/** Runs jobs until the scheduler has none left that fit. */
	void run() throws InterruptedException {
		Thread guard = Thread.ofPlatform().name("encoder-space-guard").daemon().start(this::guardSpace);
		List<Thread> threads = new java.util.ArrayList<>();
		for (int slot = 0; slot < options.workers(); slot++) {
			int id = slot;
			threads.add(Thread.ofPlatform().name("encoder-worker-" + id).start(() -> serve(id)));
		}
		for (Thread thread : threads) {
			thread.join();
		}
		guard.interrupt();
	}

	private void serve(int slot) {
		try {
			Path gameDir = workDir.resolve("workers").resolve("w" + slot);
			WorkerProfile.prepare(options.profile(), gameDir);
			for (Optional<JobScheduler.Lease> lease = scheduler.next(); lease.isPresent(); lease = scheduler.next()) {
				runWithRetries(slot, gameDir, lease.get());
			}
		} catch (IOException e) {
			EncoderLog.LOGGER.error("Worker slot {} could not prepare its game directory", slot, e);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

	/** Stops the job with the most left to write whenever the disk gets close to full. */
	private void guardSpace() {
		try {
			while (true) {
				Thread.sleep(SPACE_CHECK_MILLIS);
				scheduler.evictIfLow().ifPresent(lease -> {
					EncoderLog.LOGGER.warn("Output disk almost full: stopping {} to let the others finish", lease.job().name());
					Process process = running.get(lease);
					if (process != null) {
						process.destroyForcibly();
					}
				});
			}
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

	private void runWithRetries(int slot, Path gameDir, JobScheduler.Lease lease) throws InterruptedException {
		Job job = lease.job();
		EncoderLog.LOGGER.info("[w{}] {}: {} min of replay, about {} GB (free {} GB)", slot, job.name(),
			Math.round(lease.planned.capturedSeconds() / 60), gigabytes(lease.estimate), gigabytes(scheduler.freeBytes()));
		String failure = null;
		for (int attempt = 0; attempt <= options.retries(); attempt++) {
			long started = System.nanoTime();
			failure = attempt(slot, gameDir, lease, attempt);
			if (failure == null) {
				succeeded(lease, (System.nanoTime() - started) / 1e9);
				return;
			}
			EncoderLog.LOGGER.warn("{}: attempt {} failed: {}", job.name(), attempt + 1, failure);
			if (lease.evicted() || ranOutOfSpace(job)) {
				ended(lease, () -> report.pending(job, "ran out of disk space while encoding"));
				return;
			}
		}
		String reason = failure;
		ended(lease, () -> report.failed(job, reason));
	}

	private void succeeded(JobScheduler.Lease lease, double seconds) {
		Job job = lease.job();
		scheduler.finished(lease, OutputDisk.sizeOf(job.outputPath()), true);
		report.done(job, seconds);
		deleteTranslatedCopy(job);
	}

	/** A job that will not be retried in this run: its unfinished episode goes, then it is reported. */
	private void ended(JobScheduler.Lease lease, Runnable reportIt) {
		Job job = lease.job();
		try {
			WorkerProfile.deleteRecursively(job.outputPath());
		} catch (IOException e) {
			EncoderLog.LOGGER.error("{}: could not delete the unfinished episode {}", job.name(), job.outputPath(), e);
		}
		scheduler.finished(lease, 0, false);
		reportIt.run();
	}

	/** The translated recording is only a cache for this job; it is made again if ever needed. */
	private void deleteTranslatedCopy(Job job) {
		Path recording = job.recordingPath().toAbsolutePath();
		if (recording.startsWith(workDir.resolve("native").toAbsolutePath())) {
			try {
				Files.deleteIfExists(recording);
			} catch (IOException e) {
				EncoderLog.LOGGER.warn("{}: could not delete the translated recording {}", job.name(), recording, e);
			}
		}
	}

	private boolean ranOutOfSpace(Job job) {
		return scheduler.lowOnSpace() || logTail(logOf(job)).contains(NO_SPACE);
	}

	/** @return null on success, otherwise why it failed */
	private String attempt(int slot, Path gameDir, JobScheduler.Lease lease, int attempt) throws InterruptedException {
		Job job = lease.job();
		Path log = logOf(job);
		try {
			WorkerProfile.deleteRecursively(job.outputPath());
			Files.createDirectories(log.getParent());
			Path jobFile = gameDir.resolve("encoder-job.json");
			job.write(jobFile);
			Files.writeString(log, "=== attempt " + (attempt + 1) + " on worker " + slot + "\n", java.nio.file.StandardOpenOption.CREATE,
				java.nio.file.StandardOpenOption.APPEND);
			EncoderLog.LOGGER.info("[w{}] {} started", slot, job.name());
			Process process = launch.process(gameDir, jobFile, job.config(), options.display(), log).start();
			running.put(lease, process);
			try {
				return lease.evicted() ? stop(process) : waitFor(process, log, job.outputPath());
			} finally {
				running.remove(lease);
			}
		} catch (IOException e) {
			return "could not start the worker: " + e.getMessage();
		}
	}

	private static String stop(Process process) throws InterruptedException {
		process.destroyForcibly().waitFor();
		return "stopped: the output disk is almost full";
	}

	private String waitFor(Process process, Path log, Path episode) throws InterruptedException, IOException {
		long lastProgress = System.nanoTime();
		long lastSignal = -1;
		long stallNanos = TimeUnit.MINUTES.toNanos(options.stallMinutes());
		while (!process.waitFor(POLL_MILLIS, TimeUnit.MILLISECONDS)) {
			long signal = Files.size(log) + newestModification(episode);
			if (signal != lastSignal) {
				lastSignal = signal;
				lastProgress = System.nanoTime();
			} else if (System.nanoTime() - lastProgress > stallNanos) {
				process.destroyForcibly().waitFor();
				return "no progress for " + options.stallMinutes() + " min; killed (see " + log + ")";
			}
		}
		if (process.exitValue() != 0) {
			return "worker exited with " + process.exitValue() + " (see " + log + ")";
		}
		if (!Files.isRegularFile(episode.resolve("episode.json"))) {
			return "worker exited without finishing the episode (see " + log + ")";
		}
		return null;
	}

	private Path logOf(Job job) {
		return workDir.resolve("logs").resolve(job.name() + ".log");
	}

	private static String logTail(Path log) {
		try (RandomAccessFile file = new RandomAccessFile(log.toFile(), "r")) {
			long length = file.length();
			byte[] tail = new byte[(int) Math.min(length, LOG_TAIL_BYTES)];
			file.seek(length - tail.length);
			file.readFully(tail);
			return new String(tail, StandardCharsets.UTF_8);
		} catch (IOException e) {
			return "";
		}
	}

	private static String gigabytes(long bytes) {
		return String.format(java.util.Locale.ROOT, "%.1f", bytes / (double) (1L << 30));
	}

	private static long newestModification(Path dir) throws IOException {
		if (!Files.isDirectory(dir)) {
			return 0;
		}
		try (Stream<Path> files = Files.list(dir)) {
			return files.mapToLong(f -> f.toFile().lastModified() + f.toFile().length()).max().orElse(0);
		}
	}
}
