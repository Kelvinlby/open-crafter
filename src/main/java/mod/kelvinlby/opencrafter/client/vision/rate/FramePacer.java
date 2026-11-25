package mod.kelvinlby.opencrafter.client.vision.rate;

import mod.kelvinlby.opencrafter.core.config.VisionConfig.RateMode;

/**
 * Decides which rendered frames get captured. Pure logic, driven by the render thread.
 *
 * <ul>
 *   <li>{@code TICK}: the first rendered frame after each client tick.</li>
 *   <li>{@code FIXED_HZ}: the first frame at or after each period boundary; never bursts to catch up.</li>
 *   <li>{@code MAX_FPS}: every rendered frame.</li>
 * </ul>
 */
public final class FramePacer {
	private static final long NO_TICK = Long.MIN_VALUE;

	private long lastCapturedTick = NO_TICK;
	private long nextDueNanos = Long.MIN_VALUE;

	public boolean isDue(RateMode mode, double fixedHz, long clientTick, long nowNanos) {
		return switch (mode) {
			case TICK -> clientTick != lastCapturedTick;
			case FIXED_HZ -> nextDueNanos == Long.MIN_VALUE || nowNanos >= nextDueNanos;
			case MAX_FPS -> true;
		};
	}

	public void markCaptured(double fixedHz, long clientTick, long nowNanos) {
		lastCapturedTick = clientTick;
		nextDueNanos = nextDue(fixedHz, nowNanos);
	}

	private long nextDue(double fixedHz, long nowNanos) {
		long period = periodNanos(fixedHz);
		if (nextDueNanos == Long.MIN_VALUE || nowNanos - nextDueNanos >= period) {
			return nowNanos + period;
		}
		return nextDueNanos + period;
	}

	static long periodNanos(double hz) {
		return Math.max(1L, Math.round(1_000_000_000.0 / hz));
	}
}
