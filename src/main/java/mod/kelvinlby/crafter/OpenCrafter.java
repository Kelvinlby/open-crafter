package mod.kelvinlby.crafter;

import mod.kelvinlby.crafter.browser.BrowserManager;
import mod.kelvinlby.crafter.browser.BrowserScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OpenCrafter implements ClientModInitializer {
	public static final String MOD_ID = "open-crafter";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	private static final KeyBinding.Category CATEGORY = KeyBinding.Category.create(Identifier.of(MOD_ID, "category"));
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

		ClientLifecycleEvents.CLIENT_STARTED.register(client -> {
			BrowserManager.initialize(client.runDirectory);
		});
		ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
			BrowserManager.shutdown();
		});

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
		LOGGER.info("Open Settings key pressed");
		if (BrowserManager.isInitialized()) {
			MinecraftClient.getInstance().setScreen(
					new BrowserScreen("http://localhost:5173")
			);
		} else {
			LOGGER.warn("Browser not initialized yet");
		}
	}

	private void onStartTask() {
		// TODO: implement start task
		LOGGER.info("Start Task key pressed");
	}
}
