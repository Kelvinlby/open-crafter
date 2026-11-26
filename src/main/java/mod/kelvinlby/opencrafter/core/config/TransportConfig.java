package mod.kelvinlby.opencrafter.core.config;

import java.util.Objects;

/** Transport settings. Empty paths / 0 port / empty token mean "pick a platform default". */
public final class TransportConfig {
	/** How the control channel listens. */
	public enum Listen {
		/** TCP on loopback on Windows, a Unix domain socket elsewhere. */
		AUTO,
		UNIX,
		TCP
	}

	public Listen listen = Listen.AUTO;
	/** Unix socket file; empty = inside the discovery directory. */
	public String socketPath = "";
	/** Loopback TCP port; 0 = any free port (announced in the discovery file). */
	public int tcpPort = 0;
	/** TCP auth token; empty = a new random token every launch. */
	public String token = "";
	/** Frame ring file; empty = {@code /dev/shm} on Linux, else the discovery directory. */
	public String sharedMemoryPath = "";
	public int ringSlots = 3;

	public TransportConfig copy() {
		TransportConfig copy = new TransportConfig();
		copy.listen = listen;
		copy.socketPath = socketPath;
		copy.tcpPort = tcpPort;
		copy.token = token;
		copy.sharedMemoryPath = sharedMemoryPath;
		copy.ringSlots = ringSlots;
		return copy;
	}

	/** Whether the transport would listen differently with {@code other}. */
	public boolean sameAs(TransportConfig other) {
		return listen == other.listen
			&& Objects.equals(socketPath, other.socketPath)
			&& tcpPort == other.tcpPort
			&& Objects.equals(token, other.token)
			&& Objects.equals(sharedMemoryPath, other.sharedMemoryPath)
			&& ringSlots == other.ringSlots;
	}
}
