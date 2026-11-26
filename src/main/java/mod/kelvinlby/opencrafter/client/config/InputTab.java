package mod.kelvinlby.opencrafter.client.config;

import dev.isxander.yacl3.api.ConfigCategory;
import dev.isxander.yacl3.api.OptionGroup;
import mod.kelvinlby.opencrafter.core.config.InputConfig;

/** Limits on keys held by backends. */
final class InputTab {
	private static final InputConfig DEFAULTS = new InputConfig();

	private InputTab() {
	}

	static ConfigCategory build(InputConfig draft) {
		return ConfigCategory.createBuilder()
			.name(Texts.name("input"))
			.tooltip(Texts.description("input"))
			.group(OptionGroup.createBuilder()
				.name(Texts.name("input.holds"))
				.description(Options.description("input.holds"))
				.option(Options.intField("input.hold_lease_ms", DEFAULTS.holdLeaseMillis, 0, 60_000,
					() -> draft.holdLeaseMillis, value -> draft.holdLeaseMillis = value))
				.option(Options.intField("input.max_hold_ms", DEFAULTS.maxHoldMillis, 0, 3_600_000,
					() -> draft.maxHoldMillis, value -> draft.maxHoldMillis = value))
				.build())
			.build();
	}
}
