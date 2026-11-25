package mod.kelvinlby.opencrafter.transport.server;

import mod.kelvinlby.opencrafter.transport.fs.PrivateFiles;
import mod.kelvinlby.opencrafter.transport.rpc.Dispatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
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

/** Unix-domain-socket server accepting any number of concurrent backend connections. */
public final class SocketServer implements AutoCloseable {
	private static final Logger LOGGER = LoggerFactory.getLogger("open-crafter/transport");

	private final Path path;
	private final ServerSocketChannel server;
	private final Dispatcher dispatcher;
	private final Supplier<byte[]> hello;
	private final Set<Connection> connections = ConcurrentHashMap.newKeySet();
	private final AtomicInteger connectionCount = new AtomicInteger();
	private final AtomicLong nextId = new AtomicLong();
	private volatile boolean closed;

	private SocketServer(Path path, ServerSocketChannel server, Dispatcher dispatcher, Supplier<byte[]> hello) {
		this.path = path;
		this.server = server;
		this.dispatcher = dispatcher;
		this.hello = hello;
	}

	/**
	 * Binds {@code path} (replacing a stale socket file) and starts accepting.
	 * {@code hello} is invoked per connection and sent as its first message.
	 */
	public static SocketServer open(Path path, Dispatcher dispatcher, Supplier<byte[]> hello) throws IOException {
		PrivateFiles.prepareFresh(path);
		ServerSocketChannel channel = ServerSocketChannel.open(StandardProtocolFamily.UNIX);
		try {
			channel.bind(UnixDomainSocketAddress.of(path));
			PrivateFiles.restrictToOwner(path);
		} catch (IOException | RuntimeException e) {
			channel.close();
			throw e;
		}
		SocketServer server = new SocketServer(path, channel, dispatcher, hello);
		Thread.ofPlatform().daemon().name("open-crafter-accept").start(server::acceptLoop);
		return server;
	}

	public Path path() {
		return path;
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
		Connection connection = new Connection(nextId.incrementAndGet(), channel, dispatcher, this::unregister);
		connections.add(connection);
		connectionCount.incrementAndGet();
		try {
			connection.start(hello.get());
		} catch (RuntimeException e) {
			LOGGER.error("Could not start backend connection", e);
			connection.close();
		}
		if (closed) {
			connection.close();
		}
	}

	private void unregister(Connection connection) {
		if (connections.remove(connection)) {
			connectionCount.decrementAndGet();
		}
	}

	private void closeQuietly() {
		try {
			server.close();
		} catch (IOException e) {
			LOGGER.debug("Closing backend socket failed", e);
		}
	}

	private void deleteSocketFile() {
		try {
			Files.deleteIfExists(path);
		} catch (IOException e) {
			LOGGER.debug("Could not delete socket file {}", path, e);
		}
	}
}
