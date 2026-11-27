package mod.kelvinlby.opencrafter.core.config;

/** Animations of inventory screens. Purely visual: what the screens do is unchanged. */
public final class InventoryViewConfig {
	public static final int MIN_FLIGHT_TICKS = 1;
	public static final int MAX_FLIGHT_TICKS = 20;

	/** The backpack (player inventory) flies in from the bottom of the screen when opened and back out when closed. */
	public boolean backpackOpen = false;
	/** Ticks the flight in from the bottom lasts. */
	public int backpackFlyInTicks = 5;
	/** Ticks the flight back down lasts. */
	public int backpackFlyOutTicks = 5;
}
