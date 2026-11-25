package mod.kelvinlby.opencrafter.core.config;

/** Transport settings. Empty paths mean "pick a platform default". */
public final class TransportConfig {
	public String socketPath = "";
	public String sharedMemoryPath = "";
	public int ringSlots = 3;
}
