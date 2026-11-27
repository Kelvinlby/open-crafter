package mod.kelvinlby.opencrafter.client.inventoryview;

import mod.kelvinlby.opencrafter.api.ModContext;
import mod.kelvinlby.opencrafter.api.ModModule;

/** Inventory screen animations; purely visual, configured from the settings screen. */
public final class InventoryViewModule implements ModModule {
	@Override
	public void install(ModContext context) {
		InventoryViewHooks.install(new BackpackFlight(context.config()));
	}
}
