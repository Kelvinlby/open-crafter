package mod.kelvinlby.opencrafter.client.config;

import dev.isxander.yacl3.api.ConfigCategory;
import dev.isxander.yacl3.api.OptionGroup;
import mod.kelvinlby.opencrafter.core.config.handview.EatingConfig;
import mod.kelvinlby.opencrafter.core.config.handview.HandTransformConfig;
import mod.kelvinlby.opencrafter.core.config.handview.HandViewConfig;
import mod.kelvinlby.opencrafter.core.config.handview.SwingConfig;
import mod.kelvinlby.opencrafter.core.config.handview.Vec3Config;

/** First-person hand tweaks: a global toggle, then one section per aspect. */
final class HandViewTab {
	private static final HandViewConfig DEFAULTS = new HandViewConfig();
	private static final double SCALE_MAX = 5;
	private static final double POSITION_RANGE = 3;
	private static final double ROTATION_RANGE = 180;
	private static final double EAT_OFFSET_RANGE = 1;
	private static final double EAT_FACTOR_MAX = 3;

	private HandViewTab() {
	}

	static ConfigCategory build(HandViewConfig draft) {
		return ConfigCategory.createBuilder()
			.name(Texts.name("hand_view"))
			.tooltip(Texts.description("hand_view"))
			.option(Options.bool("hand_view.enabled", DEFAULTS.enabled, () -> draft.enabled, value -> draft.enabled = value))
			.group(general(draft))
			.group(swing(draft.swing))
			.group(transform("hand_view.main_hand", draft.mainHand))
			.group(transform("hand_view.off_hand", draft.offHand))
			.group(transform("hand_view.arm", draft.arm))
			.group(eating(draft.eating))
			.build();
	}

	private static OptionGroup general(HandViewConfig draft) {
		return group("hand_view.general")
			.option(Options.bool("hand_view.server_rotations", DEFAULTS.serverRotations, () -> draft.serverRotations, value -> draft.serverRotations = value))
			.option(Options.bool("hand_view.no_rotation_delay", DEFAULTS.noRotationDelay, () -> draft.noRotationDelay, value -> draft.noRotationDelay = value))
			.option(Options.bool("hand_view.hide_empty_hand", DEFAULTS.hideEmptyHand, () -> draft.hideEmptyHand, value -> draft.hideEmptyHand = value))
			.option(Options.bool("hand_view.skip_swapping", DEFAULTS.skipSwapping, () -> draft.skipSwapping, value -> draft.skipSwapping = value))
			.option(Options.bool("hand_view.remove_skin", DEFAULTS.removeSkin, () -> draft.removeSkin, value -> draft.removeSkin = value))
			.build();
	}

	private static OptionGroup swing(SwingConfig draft) {
		SwingConfig defaults = DEFAULTS.swing;
		return group("hand_view.swing")
			.option(Options.choice("hand_view.swing.left_click", SwingConfig.LeftClickAnimation.class, defaults.leftClick,
				() -> draft.leftClick, value -> draft.leftClick = value))
			.option(Options.choice("hand_view.swing.right_click", SwingConfig.RightClickAnimation.class, defaults.rightClick,
				() -> draft.rightClick, value -> draft.rightClick = value))
			.option(Options.bool("hand_view.swing.old_animations", defaults.oldAnimations, () -> draft.oldAnimations, value -> draft.oldAnimations = value))
			.option(Options.choice("hand_view.swing.swing_hand", SwingConfig.SwingHand.class, defaults.swingHand,
				() -> draft.swingHand, value -> draft.swingHand = value))
			.option(Options.intSlider("hand_view.swing.swing_ticks", defaults.swingTicks, SwingConfig.MIN_SWING_TICKS, SwingConfig.MAX_SWING_TICKS,
				() -> draft.swingTicks, value -> draft.swingTicks = value))
			.build();
	}

	private static OptionGroup transform(String path, HandTransformConfig draft) {
		OptionGroup.Builder group = group(path).collapsed(true);
		vec3(group, "hand_view.scale", draft.scale, Vec3Config.one(), 0, SCALE_MAX, 0.01);
		vec3(group, "hand_view.position", draft.position, Vec3Config.zero(), -POSITION_RANGE, POSITION_RANGE, 0.01);
		vec3(group, "hand_view.rotation", draft.rotation, Vec3Config.zero(), -ROTATION_RANGE, ROTATION_RANGE, 1);
		return group.build();
	}

	private static OptionGroup eating(EatingConfig draft) {
		EatingConfig defaults = DEFAULTS.eating;
		OptionGroup.Builder group = group("hand_view.eating").collapsed(true)
			.option(Options.bool("hand_view.eating.disable", defaults.disableAnimation, () -> draft.disableAnimation, value -> draft.disableAnimation = value));
		vec3(group, "hand_view.eating.offset", draft.offset, Vec3Config.zero(), -EAT_OFFSET_RANGE, EAT_OFFSET_RANGE, 0.01);
		return group
			.option(Options.slider("hand_view.eating.scale", defaults.scale, 0, EAT_FACTOR_MAX, 0.01, () -> draft.scale, value -> draft.scale = value))
			.option(Options.slider("hand_view.eating.bob", defaults.bob, 0, EAT_FACTOR_MAX, 0.01, () -> draft.bob, value -> draft.bob = value))
			.build();
	}

	/** One slider per axis, named {@code <path>.x} / {@code .y} / {@code .z}. */
	private static void vec3(OptionGroup.Builder group, String path, Vec3Config draft, Vec3Config fallback, double min, double max, double step) {
		group.option(Options.slider(path + ".x", fallback.x, min, max, step, () -> draft.x, value -> draft.x = value));
		group.option(Options.slider(path + ".y", fallback.y, min, max, step, () -> draft.y, value -> draft.y = value));
		group.option(Options.slider(path + ".z", fallback.z, min, max, step, () -> draft.z, value -> draft.z = value));
	}

	private static OptionGroup.Builder group(String path) {
		return OptionGroup.createBuilder().name(Texts.name(path)).description(Options.description(path));
	}
}
