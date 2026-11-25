package mod.kelvinlby.opencrafter.client.vision.rate;

import mod.kelvinlby.opencrafter.core.config.VisionConfig.RateMode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FramePacerTest {
	private static final long MS = 1_000_000L;

	@Test
	void tickModeCapturesFirstFrameAfterEachTick() {
		FramePacer pacer = new FramePacer();
		assertTrue(pacer.isDue(RateMode.TICK, 20, 5, 0));
		pacer.markCaptured(20, 5, 0);
		assertFalse(pacer.isDue(RateMode.TICK, 20, 5, 10 * MS));
		assertTrue(pacer.isDue(RateMode.TICK, 20, 6, 20 * MS));
	}

	@Test
	void fixedHzWaitsOnePeriod() {
		FramePacer pacer = new FramePacer();
		assertTrue(pacer.isDue(RateMode.FIXED_HZ, 10, 0, 0));
		pacer.markCaptured(10, 0, 0);
		assertFalse(pacer.isDue(RateMode.FIXED_HZ, 10, 0, 99 * MS));
		assertTrue(pacer.isDue(RateMode.FIXED_HZ, 10, 0, 100 * MS));
	}

	@Test
	void fixedHzKeepsCadenceWithoutBurstingAfterStalls() {
		FramePacer pacer = new FramePacer();
		pacer.markCaptured(10, 0, 0);
		pacer.markCaptured(10, 0, 105 * MS);
		assertFalse(pacer.isDue(RateMode.FIXED_HZ, 10, 0, 199 * MS));
		assertTrue(pacer.isDue(RateMode.FIXED_HZ, 10, 0, 200 * MS));
		pacer.markCaptured(10, 0, 900 * MS);
		assertFalse(pacer.isDue(RateMode.FIXED_HZ, 10, 0, 950 * MS));
	}

	@Test
	void maxFpsCapturesEveryFrame() {
		FramePacer pacer = new FramePacer();
		pacer.markCaptured(20, 1, 0);
		assertTrue(pacer.isDue(RateMode.MAX_FPS, 20, 1, 0));
	}
}
