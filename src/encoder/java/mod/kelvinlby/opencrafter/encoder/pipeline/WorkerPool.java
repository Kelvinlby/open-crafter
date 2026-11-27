package mod.kelvinlby.opencrafter.encoder.pipeline;

import mod.kelvinlby.opencrafter.encoder.EncoderLog;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.stream.Stream;

/**
 * Runs jobs in worker clients, {@code workers} at a time. Each worker slot keeps one game directory
 * (made once from the profile) and starts a fresh client per job, so a crash costs one recording.
 * A worker that makes no progress (no log output, no episode output) for the stall limit is killed;
 * a failed job is retried from scratch.
 */
final class WorkerPool {
	private static final long POLL_MILLIS = 2_000;

	private final EncodeOptions options;
	private final WorkerLaunch launch;
	private final Path workDir;
	private final RunReport report;
	private final Set<Process> running = ConcurrentHashMap.newKeySet();

	WorkerPool(EncodeOptions options, WorkerLaunch launch, Path workDir, RunReport report) {
		this.options = options;
		this.launch = launch;
		this.workDir = workDir;
		this.report = report;
		Runtime.getRuntime().addShutdownHook(new Thread(() -> running.forEach(Process::destroyForcibly)));
	}

	/** Takes jobs from {@code ready} until it is empty and {@code moreComing} says no more will arrive. */
	void run(BlockingQueue<Job> ready, BooleanSupplier moreComing) throws InterruptedException {
		List<Thread> threads = new java.util.ArrayList<>();
		for (int slot = 0; slot < options.workers(); slot++) {
			int id = slot;
			threads.add(Thread.ofPlatform().name("encoder-worker-" + id).start(() -> serve(id, ready, moreComing)));
		}
		for (Thread thread : threads) {
			thread.join();
		}
	}

	private void serve(int slot, BlockingQueue<Job> ready, BooleanSupplier moreComing) {
		try {
			Path gameDir = workDir.resolve("workers").resolve("w" + slot);
			WorkerProfile.prepare(options.profile(), gameDir);
			while (true) {
				Job job = ready.poll(1, TimeUnit.SECONDS);
				if (job == null) {
					if (!moreComing.getAsBoolean() && ready.isEmpty()) {
						return;
					}
					continue;
				}
				runWithRetries(slot, gameDir, job);
			}
		} catch (IOException e) {
			EncoderLog.LOGGER.error("Worker slot {} could not prepare its game directory", slot, e);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

	private void runWithRetries(int slot, Path gameDir, Job job) throws InterruptedException {
		String failure = null;
		for (int attempt = 0; attempt <= options.retries(); attempt++) {
			long started = System.nanoTime();
			failure = attempt(slot, gameDir, job, attempt);
			if (failure == null) {
				report.done(job, (System.nanoTime() - started) / 1e9);
				return;
			}
			EncoderLog.LOGGER.warn("{}: attempt {} failed: {}", job.name(), attempt + 1, failure);
		}
		report.failed(job, failure);
	}

	/** @return null on success, otherwise why it failed */
	private String attempt(int slot, Path gameDir, Job job, int attempt) throws InterruptedException {
		Path log = workDir.resolve("logs").resolve(job.name() + ".log");
		try {
			WorkerProfile.deleteRecursively(job.outputPath());
			Files.createDirectories(log.getParent());
			Path jobFile = gameDir.resolve("encoder-job.json");
			job.write(jobFile);
			Files.writeString(log, "=== attempt " + (attempt + 1) + " on worker " + slot + "\n", java.nio.file.StandardOpenOption.CREATE,
				java.nio.file.StandardOpenOption.APPEND);
			EncoderLog.LOGGER.info("[w{}] {} started", slot, job.name());
			Process process = launch.process(gameDir, jobFile, job.config(), options.display(), log).start();
			running.add(process);
			try {
				return waitFor(process, log, job.outputPath());
			} finally {
				running.remove(process);
			}
		} catch (IOException e) {
			return "could not start the worker: " + e.getMessage();
		}
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

	private static long newestModification(Path dir) throws IOException {
		if (!Files.isDirectory(dir)) {
			return 0;
		}
		try (Stream<Path> files = Files.list(dir)) {
			return files.mapToLong(f -> f.toFile().lastModified() + f.toFile().length()).max().orElse(0);
		}
	}
}
