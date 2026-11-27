package mod.kelvinlby.opencrafter.encoder.client;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class TickScheduleTest {
	@Test
	void slidingMinimumLooksBothWays() {
		long[] values = {5, 3, 8, 1, 9, 7, 6};
		assertArrayEquals(new long[]{3, 1, 1, 1, 1, 1, 6}, TickSchedule.slidingMinimum(values, 2, 2));
	}

	@Test
	void jitterIsRemovedAndLagSpikesKept() {
		Random random = new Random(7);
		int ticks = 400;
		long[] sent = new long[ticks];
		for (int k = 0; k < ticks; k++) {
			// The client stalls for 1 s before tick 200, then carries on at 20 Hz.
			sent[k] = 1_000_000 + k * 50_000L + (k >= 200 ? 1_000_000 : 0);
		}
		long[] received = new long[ticks];
		for (int k = 0; k < ticks; k++) {
			received[k] = sent[k] + 20_000 + random.nextInt(40_000);
		}
		long[] schedule = TickSchedule.smoothForTest(received);
		for (int k = 1; k < ticks; k++) {
			long step = schedule[k] - schedule[k - 1];
			if (k == 200) {
				assertEquals(1_050_000, step, 15_000, "the stall stays");
			} else {
				assertEquals(50_000, step, 5_000, "regular ticks are 50 ms apart, give or take the envelope moving");
			}
		}
	}
}
