package mod.kelvinlby.opencrafter.transport.server;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.UnixDomainSocketAddress;
import java.nio.file.Path;

/** Where the control channel listens, written as {@code unix:<path>} or {@code tcp:<host>:<port>}. */
public sealed interface Endpoint {
	SocketAddress address();

	/** The {@code unix:} / {@code tcp:} form backends accept (e.g. {@code OPENCRAFTER_ENDPOINT}). */
	String uri();

	record Unix(Path path) implements Endpoint {
		@Override
		public SocketAddress address() {
			return UnixDomainSocketAddress.of(path);
		}

		@Override
		public String uri() {
			return "unix:" + path;
		}
	}

	record Tcp(InetSocketAddress socketAddress) implements Endpoint {
		@Override
		public SocketAddress address() {
			return socketAddress;
		}

		@Override
		public String uri() {
			return "tcp:" + socketAddress.getAddress().getHostAddress() + ":" + socketAddress.getPort();
		}
	}
}
