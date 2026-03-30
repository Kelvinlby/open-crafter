package mod.kelvinlby.crafter;

import mod.kelvinlby.crafter.screen.SettingScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

public class OpenCrafter implements ClientModInitializer {
	public static final String MOD_ID = "open-crafter";

	public static MinecraftClient mc;
	public static final File FOLDER = FabricLoader.getInstance().getGameDir().resolve(MOD_ID).toFile();
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	public static KeyBinding openGuiKeybind;
	public static KeyBinding openChatKeybind;

	@Override
	public void onInitializeClient() {
		LOGGER.info("Initializing Open Crafter");

		if (!FOLDER.exists() && !FOLDER.mkdir()) {
			LOGGER.info("Data folder could not be created.");
			return;
		}

		mc = MinecraftClient.getInstance();
		createKeyBind();
		registerKeybindListeners();
	}

	private void openGui() {
		mc.setScreen(new SettingScreen());
	}

	private void openChat() {}

	private void createKeyBind() {
		KeyBinding.Category CATEGORY = KeyBinding.Category.create(Identifier.of(MOD_ID, "open-crafter"));
		openGuiKeybind = KeyBindingHelper.registerKeyBinding(new KeyBinding(
				"key.open-crafter.open-gui",
				InputUtil.Type.KEYSYM,
				GLFW.GLFW_KEY_RIGHT_CONTROL,
				CATEGORY));
		openChatKeybind = KeyBindingHelper.registerKeyBinding(new KeyBinding(
				"key.open-crafter.open-chat",
				InputUtil.Type.KEYSYM,
				GLFW.GLFW_KEY_RIGHT_ALT,
				CATEGORY));
	}

	private void registerKeybindListeners() {
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (openGuiKeybind.wasPressed()) {
				openGui();
			}

			while (openChatKeybind.wasPressed()) {
				openChat();
			}
		});
	}
}