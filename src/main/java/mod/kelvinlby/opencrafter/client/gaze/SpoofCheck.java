package mod.kelvinlby.opencrafter.client.gaze;

import mod.kelvinlby.opencrafter.client.interaction.event.Aim;
import net.minecraft.world.entity.player.Player;

/** Whether the server received a different rotation than the player's own (some mod rotated server-side only). */
final class SpoofCheck {
	/**
	 * At the end of a tick vanilla has just sent the player's own rotation, so any difference is a spoof;
	 * the margin only absorbs float noise.
	 */
	private static final float TICK_END_DEGREES = 0.01F;
	/**
	 * An action is sent mid-tick, when the mouse may have turned the view since the last movement
	 * packet; the same 1 degree as the interaction layer's crosshair alignment.
	 */
	private static final float ACTION_DEGREES = 1.0F;

	private SpoofCheck() {
	}

	static Aim own(Player player) {
		return new Aim(player.getYRot(), player.getXRot());
	}

	static boolean spoofedAtTickEnd(Aim sent, Player player) {
		return sent.degreesFrom(own(player)) > TICK_END_DEGREES;
	}

	static boolean rotatedForAction(Aim actionAim, Player player) {
		return actionAim.degreesFrom(own(player)) > ACTION_DEGREES;
	}
}
