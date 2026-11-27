package mod.kelvinlby.opencrafter.encoder.client;

import mod.kelvinlby.opencrafter.encoder.format.Phase;
import mod.kelvinlby.opencrafter.encoder.format.Rec;
import mod.kelvinlby.opencrafter.encoder.format.RecordingReader;

import java.io.IOException;
import java.util.Arrays;

/**
 * When the recorded client actually ran each of its ticks. The recorder stamps a C2S packet when
 * the server receives it, so the stamps carry network jitter: replayed as they are, a tick's input
 * would land 0, 1 or 2 replay ticks apart and the player would stutter. A client ticks every 50 ms,
 * so tick k ran at {@code 50 ms × k + offset}; the offset is the smallest delay seen around k (the
 * packets that crossed the network fastest), which leaves the client's real pauses (lag spikes)
 * in and removes the jitter. The result is on the server's clock, minus that smallest delay.
 */
final class TickSchedule {
	private static final long TICK_MICROS = 50_000;
	/** Ticks on each side over which the fastest packet sets the offset (2 s). */
	private static final int WINDOW = 40;
	/** Delay differences up to this are network jitter; larger steps are the client pausing. */
	private static final long MAX_JITTER_MICROS = 250_000;

	private final long[] micros;

	private TickSchedule(long[] micros) {
		this.micros = micros;
	}

	/** Scans a recording for its client's tick ends ({@code tickEndId}: the play packet id of ClientTickEnd). */
	static TickSchedule scan(RecordingReader reader, int tickEndId) throws IOException {
		long[] received = new long[1 << 14];
		int ticks = 0;
		for (Rec rec = reader.next(); rec != null; rec = reader.next()) {
			if (!rec.gap() && rec.c2s() && rec.phase() == Phase.PLAY && rec.packetId() == tickEndId) {
				if (ticks == received.length) {
					received = Arrays.copyOf(received, ticks * 2);
				}
				received[ticks++] = rec.timeMicros();
			}
		}
		return new TickSchedule(smooth(Arrays.copyOf(received, ticks)));
	}

	int ticks() {
		return micros.length;
	}

	/** When tick {@code index} ran (recording clock), or the last known tick plus whole ticks beyond the scan. */
	long micros(int index) {
		if (index < micros.length) {
			return micros[index];
		}
		long last = micros.length == 0 ? 0 : micros[micros.length - 1];
		return last + (index - micros.length + 1) * TICK_MICROS;
	}

	static long[] smoothForTest(long[] received) {
		return smooth(received);
	}

	/**
	 * {@code received[k] - 50 ms × k} is the tick's delay plus where the client's clock stood (which
	 * jumps when the client stalls). Its minimum over the ticks before and after k removes the
	 * jitter; where the two disagree by more than jitter can explain, a stall lies inside the window
	 * and the side that agrees with tick k is taken. Then back onto the 50 ms grid.
	 */
	private static long[] smooth(long[] received) {
		int n = received.length;
		long[] offsets = new long[n];
		for (int k = 0; k < n; k++) {
			offsets[k] = received[k] - k * TICK_MICROS;
		}
		long[] before = slidingMinimum(offsets, WINDOW, 0);
		long[] after = slidingMinimum(offsets, 0, WINDOW);
		long[] schedule = new long[n];
		for (int k = 0; k < n; k++) {
			schedule[k] = Math.min(received[k], k * TICK_MICROS + envelope(offsets[k], before[k], after[k]));
			if (k > 0) {
				schedule[k] = Math.max(schedule[k], schedule[k - 1]);
			}
		}
		return schedule;
	}

	private static long envelope(long own, long before, long after) {
		if (Math.abs(before - after) <= MAX_JITTER_MICROS) {
			return Math.min(before, after);
		}
		return Math.abs(own - before) < Math.abs(own - after) ? before : after;
	}

	/** Minimum of {@code values[k - back .. k + ahead]} for every k (monotonic deque, O(n)). */
	static long[] slidingMinimum(long[] values, int back, int ahead) {
		int n = values.length;
		long[] result = new long[n];
		int[] deque = new int[n];
		int head = 0;
		int tail = 0;
		int next = 0;
		for (int k = 0; k < n; k++) {
			int until = Math.min(n - 1, k + ahead);
			while (next <= until) {
				while (tail > head && values[deque[tail - 1]] >= values[next]) {
					tail--;
				}
				deque[tail++] = next++;
			}
			while (deque[head] < k - back) {
				head++;
			}
			result[k] = values[deque[head]];
		}
		return result;
	}
}
