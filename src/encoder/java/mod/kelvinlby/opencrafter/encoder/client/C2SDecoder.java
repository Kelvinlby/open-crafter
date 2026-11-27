package mod.kelvinlby.opencrafter.encoder.client;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import mod.kelvinlby.opencrafter.encoder.format.VarInts;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.ProtocolInfo;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.GameProtocols;
import net.minecraft.network.protocol.game.ServerGamePacketListener;
import org.jspecify.annotations.Nullable;

/** Decodes recorded C2S play packets with the client's own serverbound codec. */
final class C2SDecoder {
	private @Nullable RegistryAccess boundTo;
	private @Nullable ProtocolInfo<ServerGamePacketListener> play;

	/** The play-protocol id of a serverbound packet type, from the client's own codec. */
	static int playId(Packet<?> packet) {
		ProtocolInfo<ServerGamePacketListener> protocol = GameProtocols.SERVERBOUND_TEMPLATE.bind(RegistryFriendlyByteBuf.decorator(RegistryAccess.EMPTY), new DecodeEverything());
		ByteBuf buf = Unpooled.buffer();
		encode(protocol, buf, packet);
		return VarInts.peekVarInt(ByteBufUtil.getBytes(buf), 0);
	}

	@SuppressWarnings("unchecked")
	private static void encode(ProtocolInfo<ServerGamePacketListener> protocol, ByteBuf buf, Packet<?> packet) {
		protocol.codec().encode(buf, (Packet<? super ServerGamePacketListener>) packet);
	}

	Packet<?> decodePlay(byte[] data, RegistryAccess registries) {
		return protocol(registries).codec().decode(Unpooled.wrappedBuffer(data));
	}

	private ProtocolInfo<ServerGamePacketListener> protocol(RegistryAccess registries) {
		if (play == null || boundTo != registries) {
			play = GameProtocols.SERVERBOUND_TEMPLATE.bind(RegistryFriendlyByteBuf.decorator(registries), new DecodeEverything());
			boundTo = registries;
		}
		return play;
	}

	/** Recorded packets were valid for the server that took them: accept creative and command-block ones. */
	private static final class DecodeEverything implements GameProtocols.Context {
		@Override
		public boolean hasInfiniteMaterials() {
			return true;
		}

		@Override
		public boolean canUseCommandBlocks() {
			return true;
		}
	}
}
