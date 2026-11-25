package mod.kelvinlby.opencrafter.transport;

import mod.kelvinlby.opencrafter.api.BackendLifecycle;
import mod.kelvinlby.opencrafter.api.EventSink;
import mod.kelvinlby.opencrafter.api.frame.FramePublisher;
import mod.kelvinlby.opencrafter.api.frame.FrameSlot;
import mod.kelvinlby.opencrafter.api.frame.StreamSpec;
import mod.kelvinlby.opencrafter.api.handler.HandlerRegistry;
import mod.kelvinlby.opencrafter.core.config.TransportConfig;
import mod.kelvinlby.opencrafter.transport.discovery.DiscoveryEntry;
import mod.kelvinlby.opencrafter.transport.discovery.StaleEntries;
import mod.kelvinlby.opencrafter.transport.fs.Platform;
import mod.kelvinlby.opencrafter.transport.fs.TransportPaths;
import mod.kelvinlby.opencrafter.transport.rpc.Dispatcher;
import mod.kelvinlby.opencrafter.transport.server.Endpoint;
import mod.kelvinlby.opencrafter.transport.server.SocketServer;
import mod.kelvinlby.opencrafter.transport.shm.FrameRing;
import mod.kelvinlby.opencrafter.transport.wire.WireFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

/**
 * Shared-memory frame ring + control-channel server + discovery entry.
 * Pure Java: must not reference Minecraft classes.
 *
 * <p>Failing to open a channel is logged and leaves that channel disabled; it never prevents
 * the game from starting. {@link #reconfigure} reopens everything with new settings.
 */
public final class TransportModule implements AutoCloseable, FramePublisher {
	private static final Logger LOGGER = LoggerFactory.getLogger("open-crafter/transport");
	private static final SecureRandom RANDOM = new SecureRandom();

	private final Dispatcher dispatcher;
	private final Hello hello;
	private final BackendLifecycle lifecycle;
	private final Path discoveryDir;
	private final String instance = ProcessHandle.current().pid() + "-" + randomHex(3);
	private final String launchToken = randomHex(24);
	private final long startedMillis = System.currentTimeMillis();

	private volatile SocketServer server;
	private volatile FrameRing ring;
	private volatile DiscoveryEntry discovery;
	private volatile String token;

	private TransportModule(HandlerRegistry handlers, BackendLifecycle lifecycle, Executor mainThread, Path discoveryDir) {
		this.dispatcher = new Dispatcher(handlers, mainThread);
		this.hello = new Hello(handlers, this::ringPath);
		this.lifecycle = lifecycle;
		this.discoveryDir = discoveryDir;
	}

	public static TransportModule start(TransportConfig config, HandlerRegistry handlers, BackendLifecycle lifecycle, Executor mainThread) {
		return start(config, handlers, lifecycle, mainThread, TransportPaths.discoveryDirectory());
	}

	/** Like {@link #start(TransportConfig, HandlerRegistry, BackendLifecycle, Executor)} with an explicit discovery directory. */
	public static TransportModule start(TransportConfig config, HandlerRegistry handlers, BackendLifecycle lifecycle, Executor mainThread, Path discoveryDir) {
		TransportModule module = new TransportModule(handlers, lifecycle, mainThread, discoveryDir);
		module.open(config);
		return module;
	}

	/** Closes every channel (disconnecting backends) and opens them again with {@code config}. */
	public synchronized void reconfigure(TransportConfig config) {
		closeChannels();
		open(config);
	}

	/** Frames go to whichever ring is current, so a reconfigure never strands a module. */
	public FramePublisher frames() {
		return this;
	}

	public EventSink events() {
		return new BroadcastEventSink(() -> server);
	}

	@Override
	public boolean hasReaders() {
		FrameRing current = ring;
		return current != null && current.hasReaders();
	}

	@Override
	public FrameSlot acquire(List<StreamSpec> streams) {
		FrameRing current = ring;
		return current == null ? null : current.acquire(streams);
	}

	/** Control endpoint, or {@code null} when it could not be opened. */
	public Endpoint endpoint() {
		SocketServer current = server;
		return current == null ? null : current.endpoint();
	}

	/** TCP auth token ({@code null} for Unix sockets, which rely on file permissions). */
	public String token() {
		return endpoint() instanceof Endpoint.Tcp ? token : null;
	}

	/** Frame ring path, or {@code null} when shared memory could not be set up. */
	public Path ringPath() {
		FrameRing current = ring;
		return current == null ? null : current.path();
	}

	/** This game's discovery file, or {@code null} when it could not be written. */
	public Path discoveryFile() {
		DiscoveryEntry current = discovery;
		return current == null ? null : current.file();
	}

	public int connectionCount() {
		SocketServer current = server;
		return current == null ? 0 : current.connectionCount();
	}

	/** Human-readable state, without the token. */
	public Map<String, Object> describe() {
		Endpoint endpoint = endpoint();
		Map<String, Object> state = new LinkedHashMap<>();
		state.put("instance", instance);
		state.put("endpoint", endpoint == null ? null : endpoint.uri());
		state.put("token_required", token() != null);
		state.put("shm", ringPath());
		state.put("discovery_file", discoveryFile());
		state.put("connections", connectionCount());
		return state;
	}

	@Override
	public synchronized void close() {
		closeChannels();
	}

	private void open(TransportConfig config) {
		token = config.token == null || config.token.isBlank() ? launchToken : config.token;
		cleanStale();
		ring = openRing(config);
		server = openServer(config);
		discovery = publishDiscovery();
	}

	private void closeChannels() {
		DiscoveryEntry currentDiscovery = discovery;
		discovery = null;
		if (currentDiscovery != null) {
			currentDiscovery.close();
		}
		SocketServer currentServer = server;
		server = null;
		if (currentServer != null) {
			currentServer.close();
		}
		FrameRing currentRing = ring;
		ring = null;
		if (currentRing != null) {
			currentRing.close();
		}
	}

	private boolean hasConnections() {
		return connectionCount() > 0;
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

	private void cleanStale() {
		try {
			StaleEntries.clean(discoveryDir);
			TransportPaths.deleteStaleDevShmRings();
		} catch (IOException | RuntimeException e) {
			LOGGER.debug("Could not clean up stale transport files", e);
		}
	}

	private FrameRing openRing(TransportConfig config) {
		Path path = TransportPaths.ring(config.sharedMemoryPath, discoveryDir, instance);
		try {
			FrameRing created = FrameRing.create(path, config.ringSlots, this::hasConnections, this::announceFrame);
			LOGGER.info("Frame ring at {}", path);
			return created;
		} catch (IOException | RuntimeException e) {
			LOGGER.error("Could not create the frame ring at {}; frames are disabled", path, e);
			return null;
		}
	}

	private SocketServer openServer(TransportConfig config) {
		TransportConfig.Listen listen = resolve(config.listen);
		SocketServer opened = listen == TransportConfig.Listen.TCP ? openTcp(config) : openUnix(config);
		if (opened == null && config.listen == TransportConfig.Listen.AUTO && listen == TransportConfig.Listen.UNIX) {
			LOGGER.warn("Falling back to TCP on loopback");
			opened = openTcp(config);
		}
		return opened;
	}

	private SocketServer openUnix(TransportConfig config) {
		Path path = TransportPaths.socket(config.socketPath, discoveryDir, instance);
		try {
			SocketServer opened = SocketServer.openUnix(path, dispatcher, hello::encode, lifecycle::fireLastDisconnect);
			LOGGER.info("Backend socket at {}", opened.endpoint().uri());
			return opened;
		} catch (IOException | RuntimeException e) {
			LOGGER.error("Could not open the backend socket at {}", path, e);
			return null;
		}
	}

	private SocketServer openTcp(TransportConfig config) {
		try {
			SocketServer opened = SocketServer.openTcp(config.tcpPort, token, dispatcher, hello::encode, lifecycle::fireLastDisconnect);
			LOGGER.info("Backend socket at {}", opened.endpoint().uri());
			return opened;
		} catch (IOException | RuntimeException e) {
			LOGGER.error("Could not listen on loopback port {}; the backend cannot connect", config.tcpPort, e);
			return null;
		}
	}

	private DiscoveryEntry publishDiscovery() {
		if (server == null) {
			return null;
		}
		try {
			DiscoveryEntry entry = DiscoveryEntry.publish(discoveryDir, instance, discoveryInfo());
			LOGGER.info("Discovery file at {}", entry.file());
			return entry;
		} catch (IOException | RuntimeException e) {
			LOGGER.error("Could not write the discovery file in {}; backends must be pointed at {} manually", discoveryDir, endpoint().uri(), e);
			return null;
		}
	}

	private Map<String, Object> discoveryInfo() {
		Path ringPath = ringPath();
		Map<String, Object> info = new LinkedHashMap<>();
		info.put("format", 1);
		info.put("protocol", WireFormat.PROTOCOL_VERSION);
		info.put("instance", instance);
		info.put("pid", ProcessHandle.current().pid());
		info.put("endpoint", endpoint().uri());
		info.put("token", token());
		info.put("shm", ringPath == null ? null : ringPath.toString());
		info.put("started_ms", startedMillis);
		info.put("game_dir", Path.of("").toAbsolutePath().toString());
		info.put("flatpak", Platform.flatpakId().orElse(null));
		return info;
	}

	private static TransportConfig.Listen resolve(TransportConfig.Listen listen) {
		if (listen != TransportConfig.Listen.AUTO) {
			return listen;
		}
		return Platform.isWindows() ? TransportConfig.Listen.TCP : TransportConfig.Listen.UNIX;
	}

	private static String randomHex(int bytes) {
		byte[] value = new byte[bytes];
		RANDOM.nextBytes(value);
		return HexFormat.of().formatHex(value);
	}
}
