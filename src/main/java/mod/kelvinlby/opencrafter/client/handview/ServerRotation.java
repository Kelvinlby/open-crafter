package mod.kelvinlby.opencrafter.client.handview;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import mod.kelvinlby.opencrafter.client.interaction.Interactions;
import mod.kelvinlby.opencrafter.client.interaction.event.Aim;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;

/**
 * The rotation the server last received ({@link Interactions#sentAim}, read from outgoing movement
 * packets, so raw rotation packets count too). Vanilla always sends the player's own; a mod that
 * spoofs it leaves them apart after the tick, and then the hands can follow the sent one.
 */
final class ServerRotation {
	private static final float EPSILON_DEGREES = 1.0E-3F;

	private boolean spoofed;

	/** End of every client tick, after the movement packet went out. */
	void tick(LocalPlayer player) {
		spoofed = Interactions.sentAim(player).degreesFrom(new Aim(player.getYRot(), player.getXRot())) > EPSILON_DEGREES;
	}

	void reset() {
		spoofed = false;
	}

	/** Turns the hand {@code weight} of the way from the player's view to the sent rotation while one is spoofed. */
	void apply(PoseStack pose, LocalPlayer player, float weight) {
		if (!spoofed || weight <= 0) {
			return;
		}
		Aim sent = Interactions.sentAim(player);
		pose.rotateDegrees(Axis.XP, (player.getXRot() - sent.pitch()) * weight);
		pose.rotateDegrees(Axis.YP, Mth.wrapDegrees(player.getYRot() - sent.yaw()) * weight);
	}
}
