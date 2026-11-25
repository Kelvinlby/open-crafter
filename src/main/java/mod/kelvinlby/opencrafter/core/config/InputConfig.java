package mod.kelvinlby.opencrafter.core.config;

/** Executor settings. */
public final class InputConfig {
	/** A held key is released if the backend does not re-assert it within this time. */
	public int holdLeaseMillis = 200;
}
