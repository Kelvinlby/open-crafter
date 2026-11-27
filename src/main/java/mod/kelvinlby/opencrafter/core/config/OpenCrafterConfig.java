package mod.kelvinlby.opencrafter.core.config;

import mod.kelvinlby.opencrafter.core.config.handview.HandViewConfig;

/** Root of the persisted configuration; one section per module. */
public final class OpenCrafterConfig {
	public TransportConfig transport = new TransportConfig();
	public VisionConfig vision = new VisionConfig();
	public InputConfig input = new InputConfig();
	public HandViewConfig handView = new HandViewConfig();
	public InventoryViewConfig inventoryView = new InventoryViewConfig();
}
