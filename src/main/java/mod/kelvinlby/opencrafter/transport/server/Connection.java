package mod.kelvinlby.opencrafter.transport.server;

import mod.kelvinlby.opencrafter.transport.msgpack.MsgPackException;
import mod.kelvinlby.opencrafter.transport.msgpack.MsgPackReader;
import mod.kelvinlby.opencrafter.transport.rpc.Dispatcher;
import mod.kelvinlby.opencrafter.transport.wire.MessageInput;
import mod.kelvinlby.opencrafter.transport.wire.WireFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * One backend connection: a reader thread that decodes and dispatches requests in arrival
 * order, and a writer thread that drains the {@link Outbox} with batched writes.
 */
final class Connection {
	private static final Logger LOGGER = LoggerFactory.getLogger("open-crafter/transport");
	private static final int WRITE_BATCH_BYTES = 256 * 1024;

	private final long id;
	private final SocketChannel channel;
	private final Dispatcher dispatcher;
	private final Consumer<Connection> onClosed;
	private final Outbox outbox = new Outbox();
	private final AtomicBoolean open = new AtomicBoolean(true);
	private Supplier<byte[]> hello;
	private Handshake handshake;

	Connection(long id, SocketChannel channel, Dispatcher dispatcher, Consumer<Connection> onClosed) {
		this.id = id;
		this.channel = channel;
		this.dispatcher = dispatcher;
		this.onClosed = onClosed;
	}

	/**
	 * Starts both threads. Without a token {@code hello} is queued as the very first message;
	 * with one it is sent only once the backend authenticated.
	 */
	void start(Supplier<byte[]> hello, String token) {
		this.hello = hello;
		this.handshake = token == null ? null : new Handshake(token);
		if (handshake == null) {
			outbox.offerReply(hello.get());
		}
		Thread writer = newThread("writer", this::writeLoop);
		outbox.bindWriter(writer);
		writer.start();
		newThread("reader", this::readLoop).start();
	}

	void sendEvent(byte[] frame) {
		if (!outbox.offerEvent(frame) && outbox.droppedEvents() == 1) {
			LOGGER.warn("Backend connection {} is not keeping up; dropping events", id);
		}
	}

	void sendLatestFrame(byte[] frame) {
		outbox.offerLatestFrame(frame);
	}

	void close() {
		if (!open.compareAndSet(true, false)) {
			return;
		}
		outbox.close();
		closeChannel();
		onClosed.accept(this);
	}

	private void sendReply(byte[] frame) {
		if (!outbox.offerReply(frame)) {
			LOGGER.warn("Backend connection {} stopped reading replies; closing it", id);
			close();
		}
	}

	private void readLoop() {
		try {
			MessageInput input = new MessageInput(channel);
			MsgPackReader reader = new MsgPackReader();
			int length;
			while (open.get() && (length = input.next()) >= 0) {
				handleMessage(reader, input.array(), input.offset(), length);
			}
		} catch (IOException e) {
			logIfOpen("read", e);
		} finally {
			close();
		}
	}

	private void handleMessage(MsgPackReader reader, byte[] data, int offset, int length) {
		Map<String, Object> message = decodeMessage(reader, data, offset, length);
		if (message == null) {
			return;
		}
		if (handshake == null) {
			dispatcher.dispatch(message, this::sendReply);
		} else {
			authenticate(message);
		}
	}

	/** The first message of a TCP connection: a matching token unlocks it, anything else closes it. */
	private void authenticate(Map<String, Object> message) {
		if (!handshake.accepts(message)) {
			LOGGER.warn("Backend connection {} sent a wrong or missing token; closing it", id);
			close();
			return;
		}
		handshake = null;
		sendReply(hello.get());
	}

	@SuppressWarnings("unchecked")
	private Map<String, Object> decodeMessage(MsgPackReader reader, byte[] data, int offset, int length) {
		try {
			reader.reset(data, offset, length);
			Object value = reader.readValue();
			reader.requireFullyConsumed();
			if (value instanceof Map<?, ?> map) {
				return (Map<String, Object>) map;
			}
			reportProtocolError("Message must be a map");
		} catch (MsgPackException e) {
			reportProtocolError(e.getMessage());
		}
		return null;
	}

	/** Undecodable messages cannot be answered by id; the backend gets an {@code error} event. */
	private void reportProtocolError(String message) {
		sendReply(WireFormat.event("error", Map.of("message", message)));
	}

	private void writeLoop() {
		ByteBuffer batch = ByteBuffer.allocateDirect(WRITE_BATCH_BYTES);
		byte[] carried = null;
		try {
			while (open.get()) {
				if (carried == null) {
					outbox.awaitWork();
				}
				batch.clear();
				carried = fillBatch(batch, carried);
				batch.flip();
				if (batch.hasRemaining()) {
					writeFully(batch);
				} else if (carried != null) {
					writeFully(ByteBuffer.wrap(carried));
					carried = null;
				}
			}
		} catch (IOException e) {
			logIfOpen("write", e);
		} finally {
			close();
		}
	}

	/** Packs queued frames into {@code batch}; returns the first frame that did not fit. */
	private byte[] fillBatch(ByteBuffer batch, byte[] first) {
		byte[] next = first != null ? first : outbox.poll();
		while (next != null) {
			if (next.length > batch.remaining()) {
				return next;
			}
			batch.put(next);
			next = outbox.poll();
		}
		byte[] frame = outbox.takeLatestFrame();
		if (frame == null) {
			return null;
		}
		if (frame.length > batch.remaining()) {
			return frame;
		}
		batch.put(frame);
		return null;
	}

	private void writeFully(ByteBuffer buffer) throws IOException {
		while (buffer.hasRemaining()) {
			channel.write(buffer);
		}
	}

	private void closeChannel() {
		try {
			channel.close();
		} catch (IOException e) {
			LOGGER.debug("Closing backend connection {} failed", id, e);
		}
	}

	private void logIfOpen(String what, IOException e) {
		if (open.get()) {
			LOGGER.debug("Backend connection {} {} failed: {}", id, what, e.toString());
		}
	}

	private Thread newThread(String role, Runnable body) {
		return Thread.ofPlatform().daemon().name("open-crafter-conn-" + id + "-" + role).unstarted(body);
	}
}
