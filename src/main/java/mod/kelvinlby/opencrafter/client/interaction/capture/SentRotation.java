package mod.kelvinlby.opencrafter.client.interaction.capture;

import mod.kelvinlby.opencrafter.client.interaction.event.Aim;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;

/**
 * The rotation the server last received, read from every outgoing movement packet. Mods that
 * rotate only server-side (or send their own rotation packets) are covered the same way as vanilla.
 */
public final class SentRotation {
	private @Nullable Aim sent;

	void onMovePacket(ServerboundMovePlayerPacket packet) {
		if (packet.hasRotation()) {
			sent = new Aim(packet.getYRot(0.0F), packet.getXRot(0.0F));
		}
	}

	void reset() {
		sent = null;
	}

	/** The server's view of the player's rotation; the player's own until a rotation was sent. */
	public Aim current(Player player) {
		return sent != null ? sent : new Aim(player.getYRot(), player.getXRot());
	}
}
