package mod.kelvinlby.opencrafter.client.input;

import mod.kelvinlby.opencrafter.api.Args;
import mod.kelvinlby.opencrafter.api.ModContext;
import mod.kelvinlby.opencrafter.api.ModModule;
import mod.kelvinlby.opencrafter.api.ThreadAffinity;
import mod.kelvinlby.opencrafter.api.handler.HandlerRegistry;
import mod.kelvinlby.opencrafter.client.input.keys.KeyMappings;
import mod.kelvinlby.opencrafter.client.input.raw.RawKeyboard;
import mod.kelvinlby.opencrafter.client.input.raw.ScreenKeys;
import mod.kelvinlby.opencrafter.client.input.raw.TextTyper;
import mod.kelvinlby.opencrafter.core.config.ConfigStore;
import mod.kelvinlby.opencrafter.core.config.InputConfig;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Executors driven through Minecraft's input layer: rotation, clicks, hotbar, keybind holds, screen keys, typing. */
public final class InputModule implements ModModule {
	private final KeySimulator keys = new KeySimulator();
	private ConfigStore config;

	@Override
	public void install(ModContext context) {
		config = context.config();
		InputHooks.install(keys);
		registerEvents();
		registerOps(context.handlers());
	}

	private void registerEvents() {
		ClientTickEvents.START_CLIENT_TICK.register(client -> keys.onTickStart());
		ClientPlayConnectionEvents.DISCONNECT.register((listener, client) -> client.execute(keys::releaseAll));
	}

	private void registerOps(HandlerRegistry handlers) {
		handlers.register("input.rotate", ThreadAffinity.MAIN, args -> PlayerControls.rotate(args.number("yaw", 0), args.number("pitch", 0)));
		handlers.register("input.hotbar", ThreadAffinity.MAIN, args -> PlayerControls.selectHotbar(args.integer("slot")));
		handlers.register("input.keys", ThreadAffinity.MAIN, this::streamKeys);
		handlers.register("input.key", ThreadAffinity.MAIN, this::key);
		handlers.register("input.release_all", ThreadAffinity.MAIN, args -> releaseAll());
		handlers.register("input.keys.list", ThreadAffinity.MAIN, args -> listKeys());
		handlers.register("input.screen_key", ThreadAffinity.MAIN, InputModule::screenKey);
		handlers.register("input.type", ThreadAffinity.MAIN, args -> TextTyper.type(args.string("text")));
	}

	/** {@code down}: mapping ids to hold; every streamed key not listed is released. */
	private Object streamKeys(Args args) {
		keys.stream(args.list("down"), clampHold(args.longValue("lease_ms", inputConfig().holdLeaseMillis)));
		return null;
	}

	/** {@code key}, {@code action}: press | down | up, optional {@code duration_ms} for down. */
	private Object key(Args args) {
		String id = args.string("key");
		switch (args.string("action", "press").toLowerCase(Locale.ROOT)) {
			case "press" -> keys.press(id);
			case "down" -> keys.down(id, clampHold(args.longValue("duration_ms", inputConfig().holdLeaseMillis)));
			case "up" -> keys.up(id);
			default -> throw new IllegalArgumentException("action must be press, down or up");
		}
		return null;
	}

	private Object releaseAll() {
		keys.releaseAll();
		return null;
	}

	private List<Map<String, Object>> listKeys() {
		return Arrays.stream(Minecraft.getInstance().options.keyMappings).map(this::describe).toList();
	}

	private Map<String, Object> describe(KeyMapping mapping) {
		Map<String, Object> entry = new HashMap<>();
		entry.put("id", mapping.getName());
		entry.put("short", KeyMappings.shortId(mapping));
		entry.put("category", mapping.getCategory().id().toString());
		entry.put("key", mapping.saveString());
		entry.put("key_label", mapping.getTranslatedKeyMessage().getString());
		entry.put("down", mapping.isDown());
		entry.put("toggle_mode", KeyMappings.isToggleMode(mapping));
		entry.put("simulated", keys.isHeld(mapping));
		return entry;
	}

	/** {@code key}: physical key name, {@code modifiers}: list of shift / ctrl / alt / super / shortcut. */
	private static Object screenKey(Args args) {
		int scancode = ScreenKeys.scancode(args.string("key"));
		int modifiers = args.has("modifiers") ? ScreenKeys.modifiers(args.list("modifiers")) : 0;
		RawKeyboard.press(scancode, modifiers);
		return null;
	}

	private long clampHold(long millis) {
		return Math.clamp(millis, 0, inputConfig().maxHoldMillis);
	}

	private InputConfig inputConfig() {
		return config.get().input;
	}
}
