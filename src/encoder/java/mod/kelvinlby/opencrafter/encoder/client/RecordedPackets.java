package mod.kelvinlby.opencrafter.encoder.client;

import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ServerboundClientInformationPacket;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.ServerboundKeepAlivePacket;
import net.minecraft.network.protocol.common.ServerboundPongPacket;
import net.minecraft.network.protocol.common.ServerboundResourcePackPacket;
import net.minecraft.network.protocol.cookie.ServerboundCookieResponsePacket;
import net.minecraft.network.protocol.game.ServerboundAcceptTeleportationPacket;
import net.minecraft.network.protocol.game.ServerboundChatAckPacket;
import net.minecraft.network.protocol.game.ServerboundChatSessionUpdatePacket;
import net.minecraft.network.protocol.game.ServerboundChunkBatchReceivedPacket;
import net.minecraft.network.protocol.game.ServerboundClientTickEndPacket;
import net.minecraft.network.protocol.game.ServerboundConfigurationAcknowledgedPacket;
import net.minecraft.network.protocol.game.ServerboundDebugSubscriptionRequestPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundMoveVehiclePacket;
import net.minecraft.network.protocol.game.ServerboundPaddleBoatPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerAbilitiesPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerInputPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerLoadedPacket;

/** How the replay treats each kind of recorded C2S packet. */
final class RecordedPackets {
	private RecordedPackets() {
	}

	/** Where the body went: applied to the player at the end of its tick, where the client sends these. */
	static boolean isMotion(Packet<?> packet) {
		return packet instanceof ServerboundMovePlayerPacket
			|| packet instanceof ServerboundPlayerInputPacket
			|| packet instanceof ServerboundPlayerCommandPacket
			|| packet instanceof ServerboundPlayerAbilitiesPacket
			|| packet instanceof ServerboundMoveVehiclePacket
			|| packet instanceof ServerboundPaddleBoatPacket;
	}

	/** Protocol bookkeeping the replaying client sends on its own; replaying it would only duplicate it. */
	static boolean isBookkeeping(Packet<?> packet) {
		return packet instanceof ServerboundClientTickEndPacket
			|| packet instanceof ServerboundAcceptTeleportationPacket
			|| packet instanceof ServerboundKeepAlivePacket
			|| packet instanceof ServerboundPongPacket
			|| packet instanceof ServerboundChunkBatchReceivedPacket
			|| packet instanceof ServerboundPlayerLoadedPacket
			|| packet instanceof ServerboundClientInformationPacket
			|| packet instanceof ServerboundCustomPayloadPacket
			|| packet instanceof ServerboundChatAckPacket
			|| packet instanceof ServerboundChatSessionUpdatePacket
			|| packet instanceof ServerboundConfigurationAcknowledgedPacket
			|| packet instanceof ServerboundResourcePackPacket
			|| packet instanceof ServerboundCookieResponsePacket
			|| packet instanceof ServerboundDebugSubscriptionRequestPacket;
	}
}
