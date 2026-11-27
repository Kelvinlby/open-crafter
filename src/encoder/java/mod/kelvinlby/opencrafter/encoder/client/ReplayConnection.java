package mod.kelvinlby.opencrafter.encoder.client;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.channel.embedded.EmbeddedChannel;
import mod.kelvinlby.opencrafter.encoder.EncoderLog;
import net.minecraft.SharedConstants;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.ClientboundDisconnectPacket;
import net.minecraft.network.protocol.common.ClientboundResourcePackPushPacket;
import net.minecraft.network.protocol.common.ClientboundTransferPacket;
import net.minecraft.network.protocol.configuration.ClientboundCodeOfConductPacket;
import net.minecraft.network.protocol.configuration.ClientboundSelectKnownPacks;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundChunksBiomesPacket;
import net.minecraft.network.protocol.game.ClientboundForgetLevelChunkPacket;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.network.protocol.game.ClientboundLightUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundLoginPacket;
import net.minecraft.network.protocol.game.ClientboundRespawnPacket;
import net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket;
import net.minecraft.network.protocol.login.ClientboundHelloPacket;
import net.minecraft.network.protocol.login.ClientboundLoginCompressionPacket;
import net.minecraft.network.protocol.login.ClientboundLoginDisconnectPacket;
import net.minecraft.server.packs.repository.KnownPack;

/**
 * The client's connection to a recording instead of a server. Recorded S2C bytes enter through the
 * real frame decoder and packet codec, so every listener handles them exactly as from a server;
 * whatever the client sends goes through {@link Connection#send} (where other mods see it) and is
 * then dropped. A few packets that only make sense on a live link are filtered out.
 */
final class ReplayConnection extends Connection {
	private final EmbeddedChannel channel = new EmbeddedChannel();
	private final boolean serverResourcePacks;
	private long decodeErrors;
	private boolean sectionsTouched;

	ReplayConnection(boolean serverResourcePacks) {
		super(PacketFlow.CLIENTBOUND);
		this.serverResourcePacks = serverResourcePacks;
		configureSerialization(channel.pipeline(), PacketFlow.CLIENTBOUND, false, null);
		configurePacketHandler(channel.pipeline());
		channel.pipeline().addBefore("packet_handler", "replay_sink", new DropSentPackets());
		channel.pipeline().fireChannelActive();
	}

	/** Hands one recorded S2C packet (id + body, uncompressed) to the client, as a network frame. */
	void receive(byte[] packet) {
		ByteBuf frame = Unpooled.buffer(packet.length + 3);
		writeVarInt(frame, packet.length);
		frame.writeBytes(packet);
		channel.writeInbound(frame);
		channel.runPendingTasks();
	}

	long decodeErrors() {
		return decodeErrors;
	}

	/** True if packets since the last call changed chunk sections (so meshes are being rebuilt). */
	boolean takeSectionsTouched() {
		boolean touched = sectionsTouched;
		sectionsTouched = false;
		return touched;
	}

	@Override
	protected void channelRead0(ChannelHandlerContext ctx, Packet<?> packet) {
		if (!isLiveLinkOnly(packet)) {
			sectionsTouched |= touchesSections(packet);
			super.channelRead0(ctx, nativeKnownPacks(packet));
		}
	}

	/** A recording is replayed best effort: a packet the codec rejects is logged and skipped, never a disconnect. */
	@Override
	public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
		decodeErrors++;
		EncoderLog.warnThrottled("replay-decode", "Skipped a recorded packet the client could not decode", cause);
	}

	private boolean isLiveLinkOnly(Packet<?> packet) {
		return packet instanceof ClientboundLoginCompressionPacket
			|| packet instanceof ClientboundHelloPacket
			|| packet instanceof ClientboundLoginDisconnectPacket
			|| packet instanceof ClientboundDisconnectPacket
			|| packet instanceof ClientboundTransferPacket
			|| packet instanceof ClientboundCodeOfConductPacket
			|| packet instanceof ClientboundResourcePackPushPacket && !serverResourcePacks;
	}

	/**
	 * A server of another version offers its own vanilla data pack, so this client would not
	 * recognise it and would miss the registry contents the server left out for that pack. The
	 * recording was translated to this version, so its vanilla packs are this client's.
	 */
	private static Packet<?> nativeKnownPacks(Packet<?> packet) {
		if (!(packet instanceof ClientboundSelectKnownPacks select)) {
			return packet;
		}
		String version = SharedConstants.getCurrentVersion().id();
		return new ClientboundSelectKnownPacks(select.knownPacks().stream()
			.map(pack -> pack.isVanilla() ? new KnownPack(pack.namespace(), pack.id(), version) : pack)
			.toList());
	}

	private static boolean touchesSections(Packet<?> packet) {
		return packet instanceof ClientboundLevelChunkWithLightPacket
			|| packet instanceof ClientboundBlockUpdatePacket
			|| packet instanceof ClientboundSectionBlocksUpdatePacket
			|| packet instanceof ClientboundLightUpdatePacket
			|| packet instanceof ClientboundChunksBiomesPacket
			|| packet instanceof ClientboundForgetLevelChunkPacket
			|| packet instanceof ClientboundRespawnPacket
			|| packet instanceof ClientboundLoginPacket;
	}

	private static void writeVarInt(ByteBuf out, int value) {
		while ((value & ~0x7F) != 0) {
			out.writeByte(value & 0x7F | 0x80);
			value >>>= 7;
		}
		out.writeByte(value);
	}

	/** Swallows packets on their way out; pipeline reconfiguration messages pass. */
	private static final class DropSentPackets extends ChannelOutboundHandlerAdapter {
		@Override
		public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
			if (msg instanceof Packet<?>) {
				promise.trySuccess();
			} else {
				super.write(ctx, msg, promise);
			}
		}
	}
}
