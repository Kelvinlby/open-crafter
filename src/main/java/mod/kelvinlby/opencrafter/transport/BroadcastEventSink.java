package mod.kelvinlby.opencrafter.transport;

import mod.kelvinlby.opencrafter.api.EventSink;
import mod.kelvinlby.opencrafter.transport.msgpack.MsgPackException;
import mod.kelvinlby.opencrafter.transport.rpc.LogThrottle;
import mod.kelvinlby.opencrafter.transport.server.SocketServer;
import mod.kelvinlby.opencrafter.transport.wire.WireFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Encodes an event once and queues it on every connection. Never blocks: with no
 * connection it does nothing, a slow connection drops it, and an unencodable payload is
 * logged and dropped instead of throwing into game code.
 */
final class BroadcastEventSink implements EventSink {
	private static final Logger LOGGER = LoggerFactory.getLogger("open-crafter/transport");

	private final Supplier<SocketServer> server;
	private final LogThrottle throttle = new LogThrottle(TimeUnit.SECONDS.toNanos(1));

	BroadcastEventSink(Supplier<SocketServer> server) {
		this.server = server;
	}

	@Override
	public void publish(String event, Map<String, Object> payload) {
		Objects.requireNonNull(event, "event");
		SocketServer current = server.get();
		if (current == null || current.connectionCount() == 0) {
			return;
		}
		byte[] frame = encode(event, payload);
		if (frame != null) {
			current.broadcastEvent(frame);
		}
	}

	private byte[] encode(String event, Map<String, Object> payload) {
		try {
			return WireFormat.event(event, payload == null ? Map.of() : payload);
		} catch (MsgPackException | IllegalArgumentException e) {
			if (throttle.tryAcquire() >= 0) {
				LOGGER.warn("Dropping event '{}': {}", event, e.getMessage());
			}
			return null;
		}
	}
}
