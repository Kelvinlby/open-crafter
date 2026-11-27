package mod.kelvinlby.opencrafter.client.config;

import dev.isxander.yacl3.api.ConfigCategory;
import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.api.OptionGroup;
import mod.kelvinlby.opencrafter.core.config.ContactEffectsConfig;

import java.util.function.Consumer;
import java.util.function.Supplier;

/** Contact effects: each with its switch and its duration. */
final class ContactEffectsTab {
	private static final ContactEffectsConfig DEFAULTS = new ContactEffectsConfig();
	private static final int MAX_LINGER_TICKS = 100;

	private ContactEffectsTab() {
	}

	static ConfigCategory build(ContactEffectsConfig draft) {
		return ConfigCategory.createBuilder()
			.name(Texts.name("contact"))
			.tooltip(Texts.description("contact"))
			.group(crack(draft))
			.group(shrink(draft))
			.group(hinge(draft))
			.group(push(draft))
			.build();
	}

	private static OptionGroup crack(ContactEffectsConfig draft) {
		return group("contact.crack")
			.option(Options.bool("contact.crack.enabled", DEFAULTS.crackOverlay, () -> draft.crackOverlay, value -> draft.crackOverlay = value))
			.option(Options.intSlider("contact.crack.linger_ticks", DEFAULTS.crackLingerTicks, 0, MAX_LINGER_TICKS,
				() -> draft.crackLingerTicks, value -> draft.crackLingerTicks = value))
			.build();
	}

	private static OptionGroup shrink(ContactEffectsConfig draft) {
		return group("contact.shrink")
			.option(Options.bool("contact.shrink.enabled", DEFAULTS.breakShrink, () -> draft.breakShrink, value -> draft.breakShrink = value))
			.option(ticks("contact.shrink.ticks", DEFAULTS.breakShrinkTicks, () -> draft.breakShrinkTicks, value -> draft.breakShrinkTicks = value))
			.build();
	}

	private static OptionGroup hinge(ContactEffectsConfig draft) {
		return group("contact.hinge")
			.option(Options.bool("contact.hinge.enabled", DEFAULTS.hingeSwing, () -> draft.hingeSwing, value -> draft.hingeSwing = value))
			.option(ticks("contact.hinge.ticks", DEFAULTS.hingeSwingTicks, () -> draft.hingeSwingTicks, value -> draft.hingeSwingTicks = value))
			.build();
	}

	private static OptionGroup push(ContactEffectsConfig draft) {
		return group("contact.push")
			.option(Options.bool("contact.push.enabled", DEFAULTS.hitPush, () -> draft.hitPush, value -> draft.hitPush = value))
			.option(ticks("contact.push.ticks", DEFAULTS.hitPushTicks, () -> draft.hitPushTicks, value -> draft.hitPushTicks = value))
			.option(Options.slider("contact.push.blocks", DEFAULTS.hitPushBlocks, 0.0, ContactEffectsConfig.MAX_PUSH_BLOCKS, 0.01,
				() -> draft.hitPushBlocks, value -> draft.hitPushBlocks = value))
			.build();
	}

	private static OptionGroup.Builder group(String path) {
		return OptionGroup.createBuilder().name(Texts.name(path)).description(Options.description(path));
	}

	private static Option<Integer> ticks(String path, int fallback, Supplier<Integer> getter, Consumer<Integer> setter) {
		return Options.intSlider(path, fallback, ContactEffectsConfig.MIN_TICKS, ContactEffectsConfig.MAX_TICKS, getter, setter);
	}
}
