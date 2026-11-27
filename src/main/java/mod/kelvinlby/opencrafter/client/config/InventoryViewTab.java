package mod.kelvinlby.opencrafter.client.config;

import dev.isxander.yacl3.api.ConfigCategory;
import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.api.OptionGroup;
import mod.kelvinlby.opencrafter.core.config.InventoryViewConfig;

import java.util.function.Consumer;
import java.util.function.Supplier;

/** Inventory screen animations. */
final class InventoryViewTab {
	private static final InventoryViewConfig DEFAULTS = new InventoryViewConfig();

	private InventoryViewTab() {
	}

	static ConfigCategory build(InventoryViewConfig draft) {
		return ConfigCategory.createBuilder()
			.name(Texts.name("inventory_view"))
			.tooltip(Texts.description("inventory_view"))
			.group(backpack(draft))
			.build();
	}

	private static OptionGroup backpack(InventoryViewConfig draft) {
		return OptionGroup.createBuilder()
			.name(Texts.name("inventory_view.backpack"))
			.description(Options.description("inventory_view.backpack"))
			.option(Options.bool("inventory_view.backpack.open", DEFAULTS.backpackOpen, () -> draft.backpackOpen, value -> draft.backpackOpen = value))
			.option(flightTicks("inventory_view.backpack.fly_in_ticks", DEFAULTS.backpackFlyInTicks,
				() -> draft.backpackFlyInTicks, value -> draft.backpackFlyInTicks = value))
			.option(flightTicks("inventory_view.backpack.fly_out_ticks", DEFAULTS.backpackFlyOutTicks,
				() -> draft.backpackFlyOutTicks, value -> draft.backpackFlyOutTicks = value))
			.build();
	}

	private static Option<Integer> flightTicks(String path, int fallback, Supplier<Integer> getter, Consumer<Integer> setter) {
		return Options.intSlider(path, fallback, InventoryViewConfig.MIN_FLIGHT_TICKS, InventoryViewConfig.MAX_FLIGHT_TICKS, getter, setter);
	}
}
