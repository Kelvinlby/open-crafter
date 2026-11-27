package mod.kelvinlby.opencrafter.client.gaze;

import mod.kelvinlby.opencrafter.client.gaze.anim.HeldTarget;
import mod.kelvinlby.opencrafter.client.interaction.event.InteractionEvent;
import mod.kelvinlby.opencrafter.client.interaction.event.InteractionListener;
import mod.kelvinlby.opencrafter.client.interaction.event.Outcome;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Picks a gaze target for every action off the crosshair: the rotation the server got when the
 * action came with a rotation of its own, else the action's contact point. The target holds until
 * the action's outcome (plus the hold time); a newer action retargets.
 */
final class ActionGaze implements InteractionListener {
	private final HeldTarget<GazeTarget> targets;

	ActionGaze(HeldTarget<GazeTarget> targets) {
		this.targets = targets;
	}

	@Override
	public void onInteraction(InteractionEvent event) {
		LocalPlayer player = Minecraft.getInstance().player;
		GazeTarget target = event.crosshairAligned() || player == null ? null : targetFor(event, player);
		if (target != null) {
			targets.holdUntilOutcome(target, event.id());
		}
	}

	@Override
	public void onOutcome(InteractionEvent event, Outcome outcome) {
		targets.onOutcome(event.id(), System.nanoTime());
	}

	private static @Nullable GazeTarget targetFor(InteractionEvent event, LocalPlayer player) {
		if (SpoofCheck.rotatedForAction(event.aim(), player)) {
			return new GazeTarget.ServerAim(event.aim());
		}
		Vec3 contact = event.contact();
		return contact != null ? new GazeTarget.Contact(contact) : null;
	}
}
