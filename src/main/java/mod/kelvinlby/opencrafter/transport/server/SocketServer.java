package mod.kelvinlby.opencrafter.transport.server;

import mod.kelvinlby.opencrafter.transport.fs.PrivateFiles;
import mod.kelvinlby.opencrafter.transport.rpc.Dispatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.StandardProtocolFamily;
import java.net.StandardSocketOptions;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/**
 * Control-channel server (Unix domain socket, or TCP on loopback) accepting any number of
 * concurrent backend connections. TCP connections must authenticate with a token first.
 */
public final class SocketServer implements AutoCloseable {
	private static final Logger LOGGER = LoggerFactory.getLogger("open-crafter/transport");

	private final Endpoint endpoint;
	private final String token;
	private final ServerSocketChannel server;
	private final Dispatcher dispatcher;
	private final Supplier<byte[]> hello;
	private final Runnable onLastDisconnect;
	private final Set<Connection> connections = ConcurrentHashMap.newKeySet();
	private final AtomicInteger connectionCount = new AtomicInteger();
	private final AtomicLong nextId = new AtomicLong();
	private volatile boolean closed;

	private SocketServer(Endpoint endpoint, String token, ServerSocketChannel server, Dispatcher dispatcher, Supplier<byte[]> hello, Runnable onLastDisconnect) {
		this.endpoint = endpoint;
		this.token = token;
		this.server = server;
		this.dispatcher = dispatcher;
		this.hello = hello;
		this.onLastDisconnect = onLastDisconnect;
	}

	/**
	 * Binds the Unix socket {@code path} (replacing a stale socket file) and starts accepting.
	 * {@code hello} is invoked per connection and sent as its first message;
	 * {@code onLastDisconnect} runs whenever the connection count drops to zero.
	 */
	public static SocketServer openUnix(Path path, Dispatcher dispatcher, Supplier<byte[]> hello, Runnable onLastDisconnect) throws IOException {
		PrivateFiles.prepareFresh(path);
		Endpoint.Unix endpoint = new Endpoint.Unix(path);
		ServerSocketChannel channel = ServerSocketChannel.open(StandardProtocolFamily.UNIX);
		try {
			channel.bind(endpoint.address());
			PrivateFiles.restrictToOwner(path);
		} catch (IOException | RuntimeException e) {
			channel.close();
			throw e;
		}
		return start(new SocketServer(endpoint, null, channel, dispatcher, hello, onLastDisconnect));
	}

	/**
	 * Binds loopback {@code port} (0 = any free port) and starts accepting. Every connection must
	 * send {@code {"op": "auth", "args": {"token": token}}} first; it gets {@code hello} after that.
	 */
	public static SocketServer openTcp(int port, String token, Dispatcher dispatcher, Supplier<byte[]> hello, Runnable onLastDisconnect) throws IOException {
		ServerSocketChannel channel = ServerSocketChannel.open(StandardProtocolFamily.INET);
		try {
			channel.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), port));
		} catch (IOException | RuntimeException e) {
			channel.close();
			throw e;
		}
		Endpoint.Tcp endpoint = new Endpoint.Tcp((InetSocketAddress) channel.getLocalAddress());
		return start(new SocketServer(endpoint, token, channel, dispatcher, hello, onLastDisconnect));
	}

	private static SocketServer start(SocketServer server) {
		Thread.ofPlatform().daemon().name("open-crafter-accept").start(server::acceptLoop);
		return server;
	}

	public Endpoint endpoint() {
		return endpoint;
	}

	public int connectionCount() {
		return connectionCount.get();
	}

	/** Queues an encoded event on every connection; slow connections drop it. */
	public void broadcastEvent(byte[] frame) {
		for (Connection connection : connections) {
			connection.sendEvent(frame);
		}
	}

	/** Replaces each connection's pending frame notification with {@code frame}. */
	public void broadcastLatestFrame(byte[] frame) {
		for (Connection connection : connections) {
			connection.sendLatestFrame(frame);
		}
	}

	@Override
	public void close() {
		closed = true;
		closeQuietly();
		connections.forEach(Connection::close);
		deleteSocketFile();
	}

	private void acceptLoop() {
		try {
			while (!closed) {
				register(server.accept());
			}
		} catch (ClosedChannelException ignored) {
			// close() was called.
		} catch (IOException e) {
			if (!closed) {
				LOGGER.error("Backend socket stopped accepting connections", e);
			}
		}
	}

	private void register(SocketChannel channel) {
		setNoDelay(channel);
		Connection connection = new Connection(nextId.incrementAndGet(), channel, dispatcher, this::unregister);
		connections.add(connection);
		connectionCount.incrementAndGet();
		try {
			connection.start(hello, token);
		} catch (RuntimeException e) {
			LOGGER.error("Could not start backend connection", e);
			connection.close();
		}
		if (closed) {
			connection.close();
		}
	}

	private void unregister(Connection connection) {
		if (connections.remove(connection) && connectionCount.decrementAndGet() == 0) {
			notifyLastDisconnect();
		}
	}

	private void notifyLastDisconnect() {
		try {
			onLastDisconnect.run();
		} catch (RuntimeException e) {
			LOGGER.error("Last-disconnect listener failed", e);
		}
	}

	private void closeQuietly() {
		try {
			server.close();
		} catch (IOException e) {
			LOGGER.debug("Closing backend socket failed", e);
		}
	}

	/** Requests and replies are small: send them right away instead of coalescing. */
	private void setNoDelay(SocketChannel channel) {
		if (endpoint instanceof Endpoint.Tcp) {
			try {
				channel.setOption(StandardSocketOptions.TCP_NODELAY, true);
			} catch (IOException e) {
				LOGGER.debug("Could not disable Nagle's algorithm", e);
			}
		}
	}

	private void deleteSocketFile() {
		if (!(endpoint instanceof Endpoint.Unix(Path path))) {
			return;
		}
		try {
			Files.deleteIfExists(path);
		} catch (IOException e) {
			LOGGER.debug("Could not delete socket file {}", path, e);
		}
	}
}
