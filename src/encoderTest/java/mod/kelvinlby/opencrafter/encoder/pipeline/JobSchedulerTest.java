package mod.kelvinlby.opencrafter.encoder.pipeline;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Jobs start only while their estimated episode fits on the disk; the largest fitting one first. */
class JobSchedulerTest {
	private static final long RESERVE = 100;
	/** One byte per second of replay, so a job's estimate is its length in seconds. */
	private static final double RATE = 1.0;

	/** A disk whose free space and folder sizes the test sets. */
	private static final class FakeDisk implements JobScheduler.Disk {
		long free;
		final Map<Path, Long> used = new HashMap<>();

		FakeDisk(long free) {
			this.free = free;
		}

		@Override
		public long freeBytes() {
			return free;
		}

		@Override
		public long bytesIn(Path episode) {
			return used.getOrDefault(episode, 0L);
		}
	}

	@Test
	void startsTheLargestJobThatFits() throws InterruptedException {
		JobScheduler scheduler = scheduler(new FakeDisk(RESERVE + 500), planned("a", 100), planned("b", 900), planned("c", 400));
		assertEquals("c", scheduler.next().orElseThrow().job().name(), "b does not fit, c is the largest that does");
		assertEquals("a", scheduler.next().orElseThrow().job().name(), "c's 400 are reserved: 100 left");
	}

	@Test
	void pausesWhenNothingFitsAndNothingRuns() throws InterruptedException {
		JobScheduler scheduler = scheduler(new FakeDisk(RESERVE + 50), planned("a", 100), planned("b", 900));
		assertTrue(scheduler.next().isEmpty());
		assertTrue(scheduler.paused());
		assertEquals(2, scheduler.waiting().size(), "both stay pending");
	}

	@Test
	void writtenBytesReleaseTheReservation() throws InterruptedException {
		FakeDisk disk = new FakeDisk(RESERVE + 500);
		JobScheduler scheduler = scheduler(disk, planned("a", 400));
		JobScheduler.Lease a = scheduler.next().orElseThrow();
		assertEquals(100, scheduler.available());
		disk.used.put(a.job().outputPath(), 300L);
		disk.free -= 300;
		assertEquals(100, scheduler.available(), "written bytes moved from the reservation to the disk");
	}

	@Test
	void finishedJobsTeachTheRate() throws InterruptedException {
		JobScheduler scheduler = scheduler(new FakeDisk(1_000_000), planned("a", 100), planned("b", 10));
		JobScheduler.Lease a = scheduler.next().orElseThrow();
		scheduler.finished(a, 50, true);
		assertEquals(0.5 * SizeEstimate.MEASURED_MARGIN, scheduler.bytesPerSecond(), 1e-9);
		scheduler.finished(scheduler.next().orElseThrow(), 0, false);
		assertEquals(0.5 * SizeEstimate.MEASURED_MARGIN, scheduler.bytesPerSecond(), 1e-9, "a failed job teaches nothing");
	}

	@Test
	void evictsTheJobWithTheMostLeftWhenTheDiskIsNearlyFull() throws InterruptedException {
		FakeDisk disk = new FakeDisk(RESERVE + 1000);
		JobScheduler scheduler = scheduler(disk, planned("small", 200), planned("big", 700));
		JobScheduler.Lease big = scheduler.next().orElseThrow();
		JobScheduler.Lease small = scheduler.next().orElseThrow();
		assertTrue(scheduler.evictIfLow().isEmpty());
		disk.used.put(big.job().outputPath(), 100L);
		disk.used.put(small.job().outputPath(), 150L);
		disk.free = RESERVE / 2 - 1;
		Optional<JobScheduler.Lease> victim = scheduler.evictIfLow();
		assertEquals("big", victim.orElseThrow().job().name());
		assertTrue(big.evicted());
		assertFalse(small.evicted());
	}

	@Test
	void waitsForMoreJobsWhileTheyAreStillBeingPrepared() throws InterruptedException {
		JobScheduler scheduler = new JobScheduler(new FakeDisk(RESERVE + 500), RESERVE, RATE);
		Thread producer = Thread.ofPlatform().start(() -> {
			try {
				Thread.sleep(200);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
			scheduler.add(planned("late", 10));
			scheduler.doneAdding();
		});
		assertEquals("late", scheduler.next().orElseThrow().job().name());
		producer.join();
	}

	private static JobScheduler scheduler(FakeDisk disk, JobScheduler.Planned... jobs) {
		JobScheduler scheduler = new JobScheduler(disk, RESERVE, RATE);
		for (JobScheduler.Planned job : jobs) {
			scheduler.add(job);
		}
		scheduler.doneAdding();
		return scheduler;
	}

	private static JobScheduler.Planned planned(String name, double seconds) {
		Job job = new Job(name, name + ".mcrec", name + ".tar", Path.of("out", name).toString(), new EncodeConfig());
		return new JobScheduler.Planned(job, seconds);
	}
}
