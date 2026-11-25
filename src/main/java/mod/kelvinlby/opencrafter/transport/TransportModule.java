package mod.kelvinlby.opencrafter.transport;

import mod.kelvinlby.opencrafter.api.EventSink;
import mod.kelvinlby.opencrafter.api.frame.FramePublisher;
import mod.kelvinlby.opencrafter.api.frame.FrameSlot;
import mod.kelvinlby.opencrafter.api.frame.StreamSpec;
import mod.kelvinlby.opencrafter.api.handler.HandlerRegistry;
import mod.kelvinlby.opencrafter.core.config.TransportConfig;
import mod.kelvinlby.opencrafter.transport.fs.TransportPaths;
import mod.kelvinlby.opencrafter.transport.rpc.Dispatcher;
import mod.kelvinlby.opencrafter.transport.server.SocketServer;
import mod.kelvinlby.opencrafter.transport.shm.FrameRing;
import mod.kelvinlby.opencrafter.transport.wire.WireFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

/**
 * Shared-memory frame ring + Unix-domain-socket request server.
 * Pure Java: must not reference Minecraft classes.
 *
 * <p>Failing to open either channel is logged and leaves that channel disabled; it never
 * prevents the game from starting.
 */
public final class TransportModule implements AutoCloseable {
	private static final Logger LOGGER = LoggerFactory.getLogger("open-crafter/transport");
	private static final FramePublisher NO_FRAMES = new FramePublisher() {
		@Override
		public boolean hasReaders() {
			return false;
		}

		@Override
		public FrameSlot acquire(List<StreamSpec> streams) {
			return null;
		}
	};

	private volatile SocketServer server;
	private volatile FrameRing ring;

	private TransportModule() {
	}

	public static TransportModule start(TransportConfig config, HandlerRegistry handlers, Executor mainThread) {
		TransportModule module = new TransportModule();
		module.ring = openRing(config, module);
		module.server = openServer(config, new Dispatcher(handlers, mainThread), new Hello(handlers, module::ringPath));
		return module;
	}

	public FramePublisher frames() {
		FrameRing current = ring;
		return current == null ? NO_FRAMES : current;
	}

	public EventSink events() {
		return new BroadcastEventSink(() -> server);
	}

	/** Socket path, or {@code null} when the socket could not be opened. */
	public Path socketPath() {
		SocketServer current = server;
		return current == null ? null : current.path();
	}

	/** Frame ring path, or {@code null} when shared memory could not be set up. */
	public Path ringPath() {
		FrameRing current = ring;
		return current == null ? null : current.path();
	}

	@Override
	public void close() {
		SocketServer currentServer = server;
		if (currentServer != null) {
			currentServer.close();
		}
		FrameRing currentRing = ring;
		if (currentRing != null) {
			currentRing.close();
		}
	}

	private boolean hasConnections() {
		SocketServer current = server;
		return current != null && current.connectionCount() > 0;
	}

	/** Announces a commit; coalesced per connection, so a slow backend only sees the newest. */
	private void announceFrame(long frameIndex, int slot, long commitSequence) {
		SocketServer current = server;
		if (current == null || current.connectionCount() == 0) {
			return;
		}
		try {
			Map<String, Object> data = Map.of("index", frameIndex, "slot", slot, "seq", commitSequence);
			current.broadcastLatestFrame(WireFormat.event("frame", data));
		} catch (RuntimeException e) {
			LOGGER.warn("Could not announce frame {}", frameIndex, e);
		}
	}

	private static FrameRing openRing(TransportConfig config, TransportModule module) {
		Path path = TransportPaths.ring(config.sharedMemoryPath);
		try {
			if (TransportPaths.isDefault(config.sharedMemoryPath)) {
				deleteStaleRings();
			}
			FrameRing ring = FrameRing.create(path, config.ringSlots, module::hasConnections, module::announceFrame);
			LOGGER.info("Frame ring at {}", path);
			return ring;
		} catch (IOException | RuntimeException e) {
			LOGGER.error("Could not create the frame ring at {}; frames are disabled", path, e);
			return null;
		}
	}

	private static void deleteStaleRings() {
		try {
			TransportPaths.deleteStaleRings();
		} catch (IOException e) {
			LOGGER.debug("Could not scan for stale frame rings", e);
		}
	}

	private static SocketServer openServer(TransportConfig config, Dispatcher dispatcher, Hello hello) {
		Path path = TransportPaths.socket(config.socketPath);
		try {
			SocketServer server = SocketServer.open(path, dispatcher, hello::encode);
			LOGGER.info("Backend socket at {}", path);
			return server;
		} catch (IOException | RuntimeException e) {
			LOGGER.error("Could not open the backend socket at {}; the backend cannot connect", path, e);
			return null;
		}
	}
}
