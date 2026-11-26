package mod.kelvinlby.opencrafter.client.handview.anim;

/**
 * A level in [0, 1] that rises while a button is held and falls back to 0 once it is let go,
 * always continuing from where it is. Time is in ticks (fractional between ticks).
 */
public final class Envelope {
	private enum Phase {
		IDLE,
		RISE,
		HOLD,
		FALL
	}

	private final double riseTicks;
	private final double fallTicks;
	private Phase phase = Phase.IDLE;
	private double phaseStart;
	private double from;
	private double cycleStart;

	public Envelope(double riseTicks, double fallTicks) {
		this.riseTicks = riseTicks;
		this.fallTicks = fallTicks;
	}

	/** Starts rising from the current level; a held envelope keeps holding. */
	public void engage(double now) {
		if (phase == Phase.RISE || phase == Phase.HOLD) {
			return;
		}
		if (phase == Phase.IDLE) {
			cycleStart = now;
		}
		from = value(now);
		phase = Phase.RISE;
		phaseStart = now;
	}

	/** Once per tick: a finished rise holds while {@code held}, a released hold falls, a finished fall idles. */
	public void update(double now, boolean held) {
		if (phase == Phase.RISE && now - phaseStart >= riseTicks) {
			phase = Phase.HOLD;
			phaseStart += riseTicks;
		}
		if (phase == Phase.HOLD && !held) {
			from = 1;
			phase = Phase.FALL;
			phaseStart = now;
		}
		if (phase == Phase.FALL && now - phaseStart >= fallTicks) {
			phase = Phase.IDLE;
		}
	}

	/** Stops at once, at level 0. */
	public void reset() {
		phase = Phase.IDLE;
	}

	public boolean active() {
		return phase != Phase.IDLE;
	}

	/** Ticks since the envelope last left idle; drives oscillations so they never restart mid-motion. */
	public double cycleTime(double now) {
		return now - cycleStart;
	}

	public double value(double now) {
		double t = now - phaseStart;
		return switch (phase) {
			case IDLE -> 0;
			case RISE -> from + (1 - from) * Easing.out(t / riseTicks);
			case HOLD -> 1;
			case FALL -> from * (1 - Easing.smooth(t / fallTicks));
		};
	}
}
