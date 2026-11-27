package mod.kelvinlby.opencrafter.client.config;

import dev.isxander.yacl3.api.ConfigCategory;
import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.api.OptionGroup;
import mod.kelvinlby.opencrafter.core.config.GazeConfig;

import java.util.function.Consumer;
import java.util.function.Supplier;

/** Gaze: the rendered camera looks where the body acts. */
final class GazeTab {
	private static final GazeConfig DEFAULTS = new GazeConfig();

	private GazeTab() {
	}

	static ConfigCategory build(GazeConfig draft) {
		return ConfigCategory.createBuilder()
			.name(Texts.name("gaze"))
			.tooltip(Texts.description("gaze"))
			.option(Options.bool("gaze.enabled", DEFAULTS.enabled, () -> draft.enabled, value -> draft.enabled = value))
			.group(sources(draft))
			.group(timing(draft))
			.build();
	}

	private static OptionGroup sources(GazeConfig draft) {
		return OptionGroup.createBuilder()
			.name(Texts.name("gaze.sources"))
			.description(Options.description("gaze.sources"))
			.option(Options.bool("gaze.follow_server_aim", DEFAULTS.followServerAim,
				() -> draft.followServerAim, value -> draft.followServerAim = value))
			.option(Options.bool("gaze.look_at_actions", DEFAULTS.lookAtActions,
				() -> draft.lookAtActions, value -> draft.lookAtActions = value))
			.build();
	}

	private static OptionGroup timing(GazeConfig draft) {
		return OptionGroup.createBuilder()
			.name(Texts.name("gaze.timing"))
			.description(Options.description("gaze.timing"))
			.option(ease("gaze.ease_in_millis", DEFAULTS.easeInMillis, () -> draft.easeInMillis, value -> draft.easeInMillis = value))
			.option(Options.intField("gaze.hold_millis", DEFAULTS.holdMillis, GazeConfig.MIN_HOLD_MILLIS, GazeConfig.MAX_HOLD_MILLIS,
				() -> draft.holdMillis, value -> draft.holdMillis = value))
			.option(ease("gaze.ease_out_millis", DEFAULTS.easeOutMillis, () -> draft.easeOutMillis, value -> draft.easeOutMillis = value))
			.build();
	}

	private static Option<Integer> ease(String path, int fallback, Supplier<Integer> getter, Consumer<Integer> setter) {
		return Options.intField(path, fallback, GazeConfig.MIN_EASE_MILLIS, GazeConfig.MAX_EASE_MILLIS, getter, setter);
	}
}
