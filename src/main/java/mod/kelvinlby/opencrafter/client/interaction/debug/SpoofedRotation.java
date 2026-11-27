package mod.kelvinlby.opencrafter.client.interaction.debug;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.world.phys.Vec3;

/**
 * Silent rotations, as a rotation mod sends them: the server is told the player looks at a target
 * (a raw {@code ServerboundMovePlayerPacket.Rot}) while the player's own view stays, and is told
 * the player's own rotation again some ticks later. Does nothing when {@code holdTicks} is 0.
 */
final class SpoofedRotation {
	private final int holdTicks;
	private int restoreAt = -1;

	SpoofedRotation(int holdTicks) {
		this.holdTicks = holdTicks;
	}

	void aimAt(LocalPlayer player, Vec3 point, int ticks) {
		if (holdTicks <= 0) {
			return;
		}
		Vec3 to = point.subtract(player.getEyePosition());
		send(player, (float) Math.toDegrees(Math.atan2(-to.x, to.z)), (float) Math.toDegrees(Math.atan2(-to.y, to.horizontalDistance())));
		restoreAt = ticks + holdTicks;
	}

	void restoreIfDue(LocalPlayer player, int ticks) {
		if (restoreAt >= 0 && ticks >= restoreAt) {
			send(player, player.getYRot(), player.getXRot());
			restoreAt = -1;
		}
	}

	private static void send(LocalPlayer player, float yaw, float pitch) {
		player.connection.send(new ServerboundMovePlayerPacket.Rot(yaw, pitch, player.onGround(), player.horizontalCollision));
	}
}
