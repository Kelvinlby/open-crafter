package mod.kelvinlby.crafter;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OpenCrafter implements ClientModInitializer {
	public static final String MOD_ID = "open-crafter";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	private static final KeyBinding.Category CATEGORY = KeyBinding.Category.create(
			Identifier.of(MOD_ID, "category")
	);

	private static KeyBinding openSettingsKey;
	private static KeyBinding startTaskKey;

	@Override
	public void onInitializeClient() {
		openSettingsKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
				"key.open-crafter.open_settings",
				InputUtil.Type.KEYSYM,
				GLFW.GLFW_KEY_RIGHT_CONTROL,
				CATEGORY
		));

		startTaskKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
				"key.open-crafter.start_task",
				InputUtil.Type.KEYSYM,
				GLFW.GLFW_KEY_CAPS_LOCK,
				CATEGORY
		));

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (openSettingsKey.wasPressed()) {
				onOpenSettings();
			}
			while (startTaskKey.wasPressed()) {
				onStartTask();
			}
		});
	}

	private void onOpenSettings() {
		// TODO: implement open settings
		LOGGER.info("Open Settings key pressed");
	}

	private void onStartTask() {
		// TODO: implement start task
		LOGGER.info("Start Task key pressed");
	}
}
