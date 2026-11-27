package mod.kelvinlby.opencrafter.encoder.translate;

import com.viaversion.viaversion.api.Via;
import com.viaversion.viaversion.api.connection.ProtocolInfo;
import com.viaversion.viaversion.api.protocol.packet.State;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import com.viaversion.viaversion.connection.UserConnectionImpl;
import com.viaversion.viaversion.exception.CancelDecoderException;
import com.viaversion.viaversion.protocol.ProtocolPipelineImpl;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.channel.embedded.EmbeddedChannel;
import mod.kelvinlby.opencrafter.encoder.format.Phase;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * One Via user connection that translates raw packet bytes between a client protocol and a server
 * protocol, outside any real network. Packets Via injects on its own (one packet that becomes two,
 * replies it fakes) are caught on an embedded channel and returned in order with the packet itself.
 */
final class TranslatingConnection {
	private final EmbeddedChannel channel = new EmbeddedChannel();
	private final UserConnectionImpl user;
	private final List<byte[]> toClient = new ArrayList<>();
	private final List<byte[]> toServer = new ArrayList<>();

	/**
	 * @param clientSide true when this end is the client (translates clientbound server→client,
	 *                   like ViaFabricPlus), false when it is the server (translates serverbound client→server)
	 */
	TranslatingConnection(boolean clientSide, int clientProtocol, int serverProtocol) {
		user = new UserConnectionImpl(channel, clientSide);
		new ProtocolPipelineImpl(user);
		user.put(new PairVersionProvider.ServerVersion(ProtocolVersion.getProtocol(serverProtocol)));
		installCapture(clientSide);
		handshake(clientProtocol);
	}

	/** Translates a packet the server sent; returns the client's packets in wire order (may be empty). */
	List<byte[]> clientbound(Phase phase, byte[] packet) {
		return translate(phase, packet, true);
	}

	/** Translates a packet the client sent; returns the server's packets in wire order (may be empty). */
	List<byte[]> serverbound(Phase phase, byte[] packet) {
		return translate(phase, packet, false);
	}

	private List<byte[]> translate(Phase phase, byte[] packet, boolean clientbound) {
		setState(phase);
		toClient.clear();
		toServer.clear();
		ByteBuf buf = Unpooled.buffer(packet.length + 16).writeBytes(packet);
		List<byte[]> out = new ArrayList<>(1);
		try {
			if (clientbound) {
				user.transformClientbound(buf, CancelDecoderException::generate);
			} else {
				user.transformServerbound(buf, CancelDecoderException::generate);
			}
			out.addAll(clientbound ? toClient : toServer);
			if (buf.isReadable()) {
				out.add(ByteBufUtil.getBytes(buf));
			}
		} catch (CancelDecoderException cancelled) {
			out.addAll(clientbound ? toClient : toServer);
		} catch (Exception e) {
			throw new TranslationException(phase + (clientbound ? " S2C" : " C2S") + " packet 0x" + Integer.toHexString(packetId(packet)), e);
		} finally {
			buf.release();
		}
		toClient.clear();
		toServer.clear();
		channel.runPendingTasks();
		out.addAll(clientbound ? toClient : toServer);
		return out;
	}

	private void setState(Phase phase) {
		ProtocolInfo info = user.getProtocolInfo();
		State state = switch (phase) {
			case HANDSHAKING -> State.HANDSHAKE;
			case STATUS -> State.STATUS;
			case LOGIN -> State.LOGIN;
			case CONFIGURATION -> State.CONFIGURATION;
			case PLAY -> State.PLAY;
		};
		info.setClientState(state);
		info.setServerState(state);
	}

	/** Sends a synthetic handshake through Via so it builds the protocol path exactly as for a real join. */
	private void handshake(int clientProtocol) {
		ByteArrayOutputStream packet = new ByteArrayOutputStream();
		writeVarInt(packet, 0x00);
		writeVarInt(packet, clientProtocol);
		writeString(packet, "replay");
		packet.write(0x63);
		packet.write(0xDD);
		writeVarInt(packet, 2);
		serverbound(Phase.HANDSHAKING, packet.toByteArray());
		if (user.getProtocolInfo().getPipeline().pipes().isEmpty()) {
			throw new TranslationException("Via built no protocol pipeline for protocol " + clientProtocol, null);
		}
	}

	private void installCapture(boolean clientSide) {
		Consumer<byte[]> inbound = clientSide ? toClient::add : toServer::add;
		Consumer<byte[]> outbound = clientSide ? toServer::add : toClient::add;
		String codecName = Via.getManager().getInjector().getEncoderName();
		channel.pipeline().addLast("capture-out", new ChannelOutboundHandlerAdapter() {
			@Override
			public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) {
				capture(msg, outbound);
				promise.setSuccess();
			}
		});
		channel.pipeline().addLast(codecName, new ChannelDuplexHandler());
		channel.pipeline().addLast("capture-in", new ChannelInboundHandlerAdapter() {
			@Override
			public void channelRead(ChannelHandlerContext ctx, Object msg) {
				capture(msg, inbound);
			}
		});
	}

	private static void capture(Object msg, Consumer<byte[]> sink) {
		if (msg instanceof ByteBuf buf) {
			try {
				sink.accept(ByteBufUtil.getBytes(buf));
			} finally {
				buf.release();
			}
		}
	}

	private static int packetId(byte[] packet) {
		int result = 0;
		for (int i = 0; i < 5 && i < packet.length; i++) {
			result |= (packet[i] & 0x7F) << (7 * i);
			if ((packet[i] & 0x80) == 0) {
				break;
			}
		}
		return result;
	}

	private static void writeVarInt(ByteArrayOutputStream out, int value) {
		while ((value & ~0x7F) != 0) {
			out.write(value & 0x7F | 0x80);
			value >>>= 7;
		}
		out.write(value);
	}

	private static void writeString(ByteArrayOutputStream out, String text) {
		byte[] bytes = text.getBytes(java.nio.charset.StandardCharsets.UTF_8);
		writeVarInt(out, bytes.length);
		out.writeBytes(bytes);
	}
}
