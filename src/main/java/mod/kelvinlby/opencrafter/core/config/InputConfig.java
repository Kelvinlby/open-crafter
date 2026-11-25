package mod.kelvinlby.opencrafter.core.config;

/** Executor settings. */
public final class InputConfig {
	/** A held key is released if the backend does not re-assert it within this time. */
	public int holdLeaseMillis = 200;
	/** Upper bound for any single hold ({@code duration_ms}, {@code lease_ms}), so nothing stays stuck. */
	public int maxHoldMillis = 60_000;
}
