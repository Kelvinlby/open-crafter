package mod.kelvinlby.opencrafter.transport;

import mod.kelvinlby.opencrafter.transport.msgpack.MsgPackReader;
import mod.kelvinlby.opencrafter.transport.wire.MessageInput;
import mod.kelvinlby.opencrafter.transport.wire.WireFormat;

import java.io.IOException;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/** Minimal blocking protocol client for tests. */
final class TestClient implements AutoCloseable {
	private final SocketChannel channel;
	private final MessageInput input;
	private long nextId = 1;

	TestClient(Path socket) throws IOException {
		channel = SocketChannel.open(StandardProtocolFamily.UNIX);
		channel.connect(UnixDomainSocketAddress.of(socket));
		input = new MessageInput(channel);
	}

	void sendRaw(byte[] frame) throws IOException {
		ByteBuffer buffer = ByteBuffer.wrap(frame);
		while (buffer.hasRemaining()) {
			channel.write(buffer);
		}
	}

	long send(String op, Map<String, Object> args, boolean withId) throws IOException {
		Map<String, Object> message = new HashMap<>();
		long id = nextId++;
		if (withId) {
			message.put("id", id);
		}
		message.put("op", op);
		message.put("args", args);
		sendRaw(WireFormat.message(message));
		return id;
	}

	@SuppressWarnings("unchecked")
	Map<String, Object> read() throws IOException {
		int length = input.next();
		if (length < 0) {
			throw new IOException("closed");
		}
		return (Map<String, Object>) MsgPackReader.decode(input.array(), input.offset(), length);
	}

	/** Reads until the reply to {@code id}, skipping events. */
	Map<String, Object> replyTo(long id) throws IOException {
		while (true) {
			Map<String, Object> message = read();
			if (Long.valueOf(id).equals(message.get("id"))) {
				return message;
			}
		}
	}

	/** Reads until an event named {@code name}. */
	Map<String, Object> event(String name) throws IOException {
		while (true) {
			Map<String, Object> message = read();
			if (name.equals(message.get("event"))) {
				return message;
			}
		}
	}

	Map<String, Object> call(String op, Map<String, Object> args) throws IOException {
		return replyTo(send(op, args, true));
	}

	@Override
	public void close() throws IOException {
		channel.close();
	}
}
