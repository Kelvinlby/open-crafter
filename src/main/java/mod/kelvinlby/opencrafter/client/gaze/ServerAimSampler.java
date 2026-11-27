package mod.kelvinlby.opencrafter.client.gaze;

import mod.kelvinlby.opencrafter.client.gaze.anim.HeldTarget;
import mod.kelvinlby.opencrafter.client.interaction.Interactions;
import mod.kelvinlby.opencrafter.client.interaction.event.Aim;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

/**
 * Checks once per tick, after the movement packet went out, whether the server holds a spoofed
 * rotation; while it does the gaze follows it, and for the hold time after the last spoofed tick.
 */
final class ServerAimSampler {
	private final HeldTarget<GazeTarget> targets;

	ServerAimSampler(HeldTarget<GazeTarget> targets) {
		this.targets = targets;
	}

	/** End of every client tick. */
	void tick(Minecraft minecraft) {
		LocalPlayer player = minecraft.player;
		if (player == null) {
			return;
		}
		Aim sent = Interactions.sentAim(player);
		if (SpoofCheck.spoofedAtTickEnd(sent, player)) {
			targets.holdFrom(new GazeTarget.ServerAim(sent), System.nanoTime());
		}
	}
}
