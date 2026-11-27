package mod.kelvinlby.opencrafter.client.contact.anim;

/**
 * Progress of an effect that starts the first time it is shown, so the first frame shows it
 * exactly as the world looked before (no jump however late the outcome arrived). One that is not
 * shown soon (out of sight) starts anyway, so it still ends. Time is in client ticks including
 * the partial tick.
 */
public final class Timeline {
	/** Ticks an effect waits to be shown before it runs unseen. */
	static final double UNSEEN_TICKS = 3;

	private final double durationTicks;
	private final double created;
	private double start = Double.NaN;

	public Timeline(double durationTicks, double created) {
		this.durationTicks = Math.max(durationTicks, 1.0e-3);
		this.created = created;
	}

	/** Progress in {@code [0, 1]} at {@code now}; the first call starts the timeline. */
	public double progress(double now) {
		if (!started()) {
			start = now;
		}
		return Easing.clamp01((now - start) / durationTicks);
	}

	public boolean started() {
		return !Double.isNaN(start);
	}

	/** Run to its end by {@code now}. */
	public boolean finished(double now) {
		if (!started() && now - created >= UNSEEN_TICKS) {
			start = now;
		}
		return started() && now - start >= durationTicks;
	}
}
