package mod.kelvinlby.opencrafter.client.config;

import dev.isxander.yacl3.api.YetAnotherConfigLib;
import mod.kelvinlby.opencrafter.client.vision.VisionControl;
import mod.kelvinlby.opencrafter.core.config.ConfigStore;
import mod.kelvinlby.opencrafter.core.config.OpenCrafterConfig;
import mod.kelvinlby.opencrafter.transport.TransportModule;
import net.minecraft.client.gui.screens.Screen;
import org.jspecify.annotations.Nullable;

/**
 * The Open Crafter settings screen (YACL). Every tab edits one draft copy of the settings;
 * Save hands the draft to the {@link ConfigStore}, whose listeners apply it.
 */
public final class SettingsScreen {
	private static @Nullable SettingsScreen instance;

	private final ConfigStore config;
	private final TransportModule transport;
	private final VisionControl vision;

	private SettingsScreen(ConfigStore config, TransportModule transport, VisionControl vision) {
		this.config = config;
		this.transport = transport;
		this.vision = vision;
	}

	/** Makes the screen available to {@link #create} (command, Mod Menu). */
	public static void install(ConfigStore config, TransportModule transport, VisionControl vision) {
		instance = new SettingsScreen(config, transport, vision);
	}

	public static Screen create(@Nullable Screen parent) {
		if (instance == null) {
			throw new IllegalStateException("Open Crafter is not initialised yet");
		}
		return instance.build(parent);
	}

	private Screen build(@Nullable Screen parent) {
		OpenCrafterConfig draft = config.copy();
		return YetAnotherConfigLib.createBuilder()
			.title(Texts.name("title"))
			.category(HandViewTab.build(draft.handView))
			.category(InventoryViewTab.build(draft.inventoryView))
			.category(GazeTab.build(draft.gaze))
			.category(ContactEffectsTab.build(draft.contactEffects))
			.category(VisionTab.build(draft.vision, vision))
			.category(InputTab.build(draft.input))
			.category(TransportTab.build(draft.transport, transport))
			// The draft stays editable after a save, so the store gets its own copy
			.save(() -> config.replace(ConfigStore.copyOf(draft)))
			.build()
			.generateScreen(parent);
	}
}
