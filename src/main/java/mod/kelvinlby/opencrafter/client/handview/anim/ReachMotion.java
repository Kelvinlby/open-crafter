package mod.kelvinlby.opencrafter.client.handview.anim;

/**
 * Block placement: the held item travels out to the placed block and back over one swing.
 * {@link #weight} is 0 at rest and 1 at the block, easing in and out at both ends.
 */
public final class ReachMotion {
	private double start;
	private double durationTicks;
	private boolean active;

	/** Starts a reach; one still under way continues from its current weight so nothing jumps. */
	public void start(double now, double durationTicks) {
		double current = weight(now);
		this.durationTicks = Math.max(1, durationTicks);
		this.start = now - Easing.inverseSmooth(current) / 2 * this.durationTicks;
		this.active = true;
	}

	public void update(double now) {
		if (active && now - start >= durationTicks) {
			active = false;
		}
	}

	public void reset() {
		active = false;
	}

	public boolean active() {
		return active;
	}

	public float weight(double now) {
		if (!active) {
			return 0;
		}
		double p = (now - start) / durationTicks;
		if (p >= 1) {
			return 0;
		}
		return (float) Easing.smooth(p < 0.5 ? 2 * p : 2 - 2 * p);
	}
}
