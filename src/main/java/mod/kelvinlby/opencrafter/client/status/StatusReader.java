package mod.kelvinlby.opencrafter.client.status;

import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.food.FoodData;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/** Builds the status snapshot from client state. Main thread only. */
final class StatusReader {
	private static final int INFINITE_DURATION = -1;

	private StatusReader() {
	}

	static Map<String, Object> read() {
		Minecraft minecraft = Minecraft.getInstance();
		Map<String, Object> status = new LinkedHashMap<>();
		status.put("window", window(minecraft.getWindow()));
		status.put("screen", screenName(minecraft.gui.screen()));
		status.put("paused", minecraft.isPaused());
		LocalPlayer player = minecraft.player;
		status.put("in_world", player != null && minecraft.level != null);
		if (player != null && minecraft.level != null) {
			status.putAll(playerStatus(minecraft, player));
		}
		return status;
	}

	private static Map<String, Object> window(Window window) {
		Map<String, Object> result = new LinkedHashMap<>();
		result.put("width", window.getWidth());
		result.put("height", window.getHeight());
		result.put("screen_width", window.getScreenWidth());
		result.put("screen_height", window.getScreenHeight());
		result.put("gui_scale", window.getGuiScale());
		result.put("gui_width", window.getGuiScaledWidth());
		result.put("gui_height", window.getGuiScaledHeight());
		result.put("focused", window.isFocused());
		return result;
	}

	private static String screenName(Screen screen) {
		return screen == null ? null : screen.getClass().getSimpleName();
	}

	private static Map<String, Object> playerStatus(Minecraft minecraft, LocalPlayer player) {
		Map<String, Object> result = new LinkedHashMap<>();
		result.put("game_mode", minecraft.gameMode.getPlayerMode().getName());
		result.put("dead", player.isDeadOrDying());
		result.put("selected_slot", player.getInventory().getSelectedSlot());
		result.put("rotation", rotation(player));
		result.put("health", health(player));
		result.put("food", food(player.getFoodData()));
		result.put("air", air(minecraft, player));
		result.put("effects", effects(player));
		return result;
	}

	private static Map<String, Object> rotation(LocalPlayer player) {
		return Map.of(
			"yaw", (double) Mth.wrapDegrees(player.getYRot()),
			"pitch", (double) player.getXRot(),
			"yaw_raw", (double) player.getYRot()
		);
	}

	private static Map<String, Object> health(LocalPlayer player) {
		return Map.of(
			"current", (double) player.getHealth(),
			"max", (double) player.getMaxHealth(),
			"absorption", (double) player.getAbsorptionAmount()
		);
	}

	private static Map<String, Object> food(FoodData food) {
		return Map.of(
			"level", food.getFoodLevel(),
			"saturation", (double) food.getSaturationLevel()
		);
	}

	private static Map<String, Object> air(Minecraft minecraft, LocalPlayer player) {
		int supply = player.getAirSupply();
		int max = player.getMaxAirSupply();
		boolean eyesInWater = player.isEyeInFluid(FluidTags.WATER);
		AirBubbles bubbles = AirBubbles.of(supply, max, eyesInWater);
		boolean hudShown = !minecraft.gui.hud.isHidden()
			&& minecraft.gameMode.canHurtPlayer()
			&& AirBubbles.drawn(supply, max, eyesInWater);
		Map<String, Object> result = new LinkedHashMap<>();
		result.put("supply", supply);
		result.put("max", max);
		result.put("eyes_in_water", eyesInWater);
		result.put("hud_shown", hudShown);
		result.put("bubbles", bubbles.full());
		result.put("bubble_popping", bubbles.popping());
		result.put("bubbles_empty", bubbles.empty());
		return result;
	}

	private static Map<String, Object> effects(LocalPlayer player) {
		Map<String, Object> result = new HashMap<>();
		for (MobEffectInstance effect : player.getActiveEffects()) {
			result.put(effect.getEffect().getRegisteredName(), effect(effect));
		}
		return result;
	}

	private static Map<String, Object> effect(MobEffectInstance effect) {
		return Map.of(
			"amplifier", effect.getAmplifier(),
			"level", effect.getAmplifier() + 1,
			"duration_ticks", effect.isInfiniteDuration() ? INFINITE_DURATION : effect.getDuration(),
			"ambient", effect.isAmbient(),
			"visible", effect.isVisible(),
			"show_icon", effect.showIcon()
		);
	}
}
