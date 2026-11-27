package mod.kelvinlby.opencrafter.core.config;

/** The interaction event layer: every in-world action the player sends, whoever issued it. */
public final class InteractionConfig {
	public static final int MIN_OUTCOME_WINDOW_MILLIS = 50;
	public static final int MAX_OUTCOME_WINDOW_MILLIS = 5000;

	/**
	 * How long an action waits for the world to confirm it before its outcome is {@code none}.
	 * Wall-clock time, not ticks: a slow client runs several ticks per frame, before the server's reply is handled.
	 */
	public int outcomeWindowMillis = 500;
}
