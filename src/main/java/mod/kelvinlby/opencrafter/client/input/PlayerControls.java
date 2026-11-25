package mod.kelvinlby.opencrafter.client.input;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Inventory;

import java.util.Map;

/** Direct player controls that vanilla drives from raw mouse input: look rotation and hotbar selection. */
final class PlayerControls {
	/** {@code Entity.turn} multiplies its mouse-unit arguments by this to get degrees. */
	private static final double DEGREES_PER_TURN_UNIT = 0.15;

	private PlayerControls() {
	}

	/**
	 * Turns like mouse look via {@code Entity.turn}: rotation and previous-tick rotation move
	 * together, so the camera changes immediately without interpolation lag or snapping; pitch is
	 * clamped to [-90, 90] and vehicles get {@code onPassengerTurned}.
	 */
	static Map<String, Object> rotate(double yawDegrees, double pitchDegrees) {
		requireFinite(yawDegrees, "yaw");
		requireFinite(pitchDegrees, "pitch");
		LocalPlayer player = requirePlayer();
		player.turn(yawDegrees / DEGREES_PER_TURN_UNIT, pitchDegrees / DEGREES_PER_TURN_UNIT);
		return Map.of("yaw", (double) player.getYRot(), "pitch", (double) player.getXRot());
	}

	/** Selects a hotbar slot like the hotbar keys do; the server learns it from the next gameMode tick. */
	static Map<String, Object> selectHotbar(int slot) {
		if (slot < 0 || slot >= Inventory.getSelectionSize()) {
			throw new IllegalArgumentException("Hotbar slot must be 0-8, got " + slot);
		}
		LocalPlayer player = requirePlayer();
		if (player.isSpectator()) {
			Minecraft.getInstance().gui.hud.getSpectatorGui().onHotbarSelected(slot);
		} else {
			player.getInventory().setSelectedSlot(slot);
		}
		return Map.of("slot", player.getInventory().getSelectedSlot());
	}

	private static LocalPlayer requirePlayer() {
		LocalPlayer player = Minecraft.getInstance().player;
		if (player == null) {
			throw new IllegalStateException("Not in a world");
		}
		return player;
	}

	private static void requireFinite(double value, String name) {
		if (!Double.isFinite(value)) {
			throw new IllegalArgumentException(name + " must be finite");
		}
	}
}
