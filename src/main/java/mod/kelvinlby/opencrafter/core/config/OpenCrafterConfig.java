package mod.kelvinlby.opencrafter.core.config;

/** Root of the persisted configuration; one section per module. */
public final class OpenCrafterConfig {
	public TransportConfig transport = new TransportConfig();
	public VisionConfig vision = new VisionConfig();
	public InputConfig input = new InputConfig();
}
