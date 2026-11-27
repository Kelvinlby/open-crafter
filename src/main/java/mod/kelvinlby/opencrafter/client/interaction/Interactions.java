package mod.kelvinlby.opencrafter.client.interaction;

import mod.kelvinlby.opencrafter.client.interaction.capture.SentRotation;
import mod.kelvinlby.opencrafter.client.interaction.event.Aim;
import mod.kelvinlby.opencrafter.client.interaction.event.InteractionBus;
import mod.kelvinlby.opencrafter.client.interaction.event.InteractionListener;
import net.minecraft.world.entity.player.Player;

/**
 * What other client modules use of the interaction layer: subscribing to every in-world action
 * of the local player, and the rotation the server last received.
 */
public final class Interactions {
	static final InteractionBus BUS = new InteractionBus();
	static final SentRotation ROTATION = new SentRotation();

	private Interactions() {
	}

	/** Called on the client thread for every action and outcome, in send order. */
	public static void subscribe(InteractionListener listener) {
		BUS.subscribe(listener);
	}

	/** The rotation the server last received for the player (spoofed by rotation mods, or the player's own). */
	public static Aim sentAim(Player player) {
		return ROTATION.current(player);
	}
}
