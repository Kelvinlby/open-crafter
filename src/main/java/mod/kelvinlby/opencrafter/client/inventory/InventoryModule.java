package mod.kelvinlby.opencrafter.client.inventory;

import mod.kelvinlby.opencrafter.api.ModContext;
import mod.kelvinlby.opencrafter.api.ModModule;
import mod.kelvinlby.opencrafter.api.handler.HandlerRegistry;

/**
 * Inventory / container executors: every menu interaction the vanilla client can perform,
 * driven through the open screen's own handlers. Ops are documented in {@code docs/ops/inventory.md}.
 */
public final class InventoryModule implements ModModule {
	@Override
	public void install(ModContext context) {
		HandlerRegistry handlers = context.handlers();
		ScreenOps.register(handlers);
		ClickOps.register(handlers);
		MenuButtonOps.register(handlers);
		StationOps.register(handlers);
		BundleOps.register(handlers);
		RecipeOps.register(handlers);
		CreativeOps.register(handlers);
	}
}
