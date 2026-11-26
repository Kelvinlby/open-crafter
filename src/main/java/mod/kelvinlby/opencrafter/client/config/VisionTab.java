package mod.kelvinlby.opencrafter.client.config;

import dev.isxander.yacl3.api.ConfigCategory;
import dev.isxander.yacl3.api.OptionGroup;
import mod.kelvinlby.opencrafter.client.vision.VisionControl;
import mod.kelvinlby.opencrafter.client.vision.VisionSettings;
import mod.kelvinlby.opencrafter.core.config.VisionConfig;

/** How often frames are pushed to backends. */
final class VisionTab {
	private static final VisionConfig DEFAULTS = new VisionConfig();

	private VisionTab() {
	}

	static ConfigCategory build(VisionConfig draft, VisionControl control) {
		return ConfigCategory.createBuilder()
			.name(Texts.name("vision"))
			.tooltip(Texts.description("vision"))
			.group(rate(draft))
			.group(StatusLabels.group("vision.status", control.status()))
			.build();
	}

	private static OptionGroup rate(VisionConfig draft) {
		return OptionGroup.createBuilder()
			.name(Texts.name("vision.rate"))
			.description(Options.description("vision.rate"))
			.option(Options.choice("vision.rate_mode", VisionConfig.RateMode.class, DEFAULTS.rateMode, () -> draft.rateMode, value -> draft.rateMode = value))
			.option(Options.doubleField("vision.fixed_hz", DEFAULTS.fixedHz, VisionSettings.MIN_HZ, VisionSettings.MAX_HZ,
				() -> draft.fixedHz, value -> draft.fixedHz = value))
			.build();
	}
}
