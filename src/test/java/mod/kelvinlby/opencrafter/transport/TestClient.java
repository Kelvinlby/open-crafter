package mod.kelvinlby.opencrafter.transport;

import mod.kelvinlby.opencrafter.transport.msgpack.MsgPackReader;
import mod.kelvinlby.opencrafter.transport.server.Endpoint;
import mod.kelvinlby.opencrafter.transport.wire.MessageInput;
import mod.kelvinlby.opencrafter.transport.wire.WireFormat;

import java.io.IOException;
import java.net.StandardProtocolFamily;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.HashMap;
import java.util.Map;

/** Minimal blocking protocol client for tests. */
final class TestClient implements AutoCloseable {
	private final SocketChannel channel;
	private final MessageInput input;
	private long nextId = 1;

	TestClient(Endpoint endpoint) throws IOException {
		channel = SocketChannel.open(endpoint instanceof Endpoint.Unix ? StandardProtocolFamily.UNIX : StandardProtocolFamily.INET);
		channel.connect(endpoint.address());
		input = new MessageInput(channel);
	}

	/** Connects to a TCP endpoint and sends the auth message first. */
	TestClient(Endpoint endpoint, String token) throws IOException {
		this(endpoint);
		send("auth", Map.of("token", token), false);
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
