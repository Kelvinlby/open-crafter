package mod.kelvinlby.opencrafter.encoder.pipeline;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Hands jobs to workers only while their episodes fit on the output disk. Every running job holds
 * a reservation (its estimated size minus what it has written so far); a job starts only if its
 * own estimate fits in the free space minus the reservations and a fixed reserve. Among the jobs
 * that fit, the largest goes first, so the long recordings start early and the small ones fill
 * the space that is left at the end. When nothing fits and nothing is running, the run pauses:
 * whatever is still waiting stays pending for the next run.
 */
final class JobScheduler {
	private static final long WAIT_MILLIS = 1_000;

	/** The output disk. */
	interface Disk {
		long freeBytes();

		/** Bytes an episode folder holds now. */
		long bytesIn(Path episode);
	}

	/** A job and how much replay time it covers. */
	record Planned(Job job, double capturedSeconds) {
	}

	/** A started job: what it reserved, and whether it was stopped to free space. */
	static final class Lease {
		final Planned planned;
		final long estimate;
		private volatile boolean evicted;

		private Lease(Planned planned, long estimate) {
			this.planned = planned;
			this.estimate = estimate;
		}

		Job job() {
			return planned.job();
		}

		boolean evicted() {
			return evicted;
		}
	}

	private final Disk disk;
	private final long reserveBytes;
	private final double initialBytesPerSecond;
	private final List<Planned> waiting = new ArrayList<>();
	private final List<Lease> running = new ArrayList<>();
	private double measuredBytesPerSecond;
	private boolean producing = true;
	private boolean paused;

	JobScheduler(Disk disk, long reserveBytes, double initialBytesPerSecond) {
		this.disk = disk;
		this.reserveBytes = reserveBytes;
		this.initialBytesPerSecond = initialBytesPerSecond;
	}

	synchronized void add(Planned planned) {
		waiting.add(planned);
		notifyAll();
	}

	/** No more jobs will be added. */
	synchronized void doneAdding() {
		producing = false;
		notifyAll();
	}

	/**
	 * The next job that fits, waiting while one may still come or space may still free up.
	 * Empty when the run is over: everything started, or nothing left fits.
	 */
	synchronized Optional<Lease> next() throws InterruptedException {
		while (true) {
			if (paused || waiting.isEmpty() && !producing) {
				return Optional.empty();
			}
			Optional<Lease> started = startLargestFitting();
			if (started.isPresent()) {
				return started;
			}
			if (running.isEmpty() && !producing) {
				paused = true;
				notifyAll();
				return Optional.empty();
			}
			wait(WAIT_MILLIS);
		}
	}

	/** A job ended; a successful one teaches the byte rate. */
	synchronized void finished(Lease lease, long bytesWritten, boolean success) {
		running.remove(lease);
		if (success && lease.planned.capturedSeconds() > 0) {
			measuredBytesPerSecond = Math.max(measuredBytesPerSecond, bytesWritten / lease.planned.capturedSeconds());
		}
		notifyAll();
	}

	/**
	 * When free space fell below half the reserve (estimates were too low), marks the running job
	 * with the most left to write as evicted and returns it, so the caller can stop it.
	 */
	synchronized Optional<Lease> evictIfLow() {
		if (!lowOnSpace()) {
			return Optional.empty();
		}
		Optional<Lease> victim = running.stream().filter(l -> !l.evicted)
			.max(Comparator.comparingLong(this::stillReserved).thenComparingLong(l -> l.estimate));
		victim.ifPresent(l -> l.evicted = true);
		return victim;
	}

	/** Free space is below half the reserve: running jobs are about to fill the disk. */
	synchronized boolean lowOnSpace() {
		return disk.freeBytes() < reserveBytes / 2;
	}

	long freeBytes() {
		return disk.freeBytes();
	}

	/** Jobs that never started (pending for the next run). */
	synchronized List<Planned> waiting() {
		return List.copyOf(waiting);
	}

	synchronized boolean paused() {
		return paused;
	}

	/** What a job is estimated to need, at the current rate. */
	synchronized long estimate(Planned planned) {
		return SizeEstimate.bytes(planned.capturedSeconds(), bytesPerSecond());
	}

	/** Free space a new job may use: free bytes minus the reserve and what running jobs still need. */
	synchronized long available() {
		long reserved = running.stream().mapToLong(this::stillReserved).sum();
		return disk.freeBytes() - reserveBytes - reserved;
	}

	synchronized double bytesPerSecond() {
		return measuredBytesPerSecond > 0 ? measuredBytesPerSecond * SizeEstimate.MEASURED_MARGIN : initialBytesPerSecond;
	}

	private Optional<Lease> startLargestFitting() {
		long available = available();
		Optional<Planned> pick = waiting.stream().filter(p -> estimate(p) <= available)
			.max(Comparator.comparingDouble(Planned::capturedSeconds));
		return pick.map(p -> {
			waiting.remove(p);
			Lease lease = new Lease(p, estimate(p));
			running.add(lease);
			return lease;
		});
	}

	private long stillReserved(Lease lease) {
		return Math.max(0, lease.estimate - disk.bytesIn(lease.job().outputPath()));
	}
}
