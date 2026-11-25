package mod.kelvinlby.opencrafter.core.config;

/** Frame pushing settings. */
public final class VisionConfig {
	public enum RateMode {
		/** One frame per client tick (20 Hz at normal tick rate). */
		TICK,
		/** A fixed frequency, see {@link #fixedHz}. */
		FIXED_HZ,
		/** Every rendered frame. */
		MAX_FPS
	}

	public RateMode rateMode = RateMode.TICK;
	public double fixedHz = 20.0;
}
