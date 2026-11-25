package mod.kelvinlby.opencrafter.transport.server;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;

/** Checks the {@code {"op": "auth", "args": {"token": ...}}} message that opens a TCP connection. */
final class Handshake {
	private final byte[] token;

	Handshake(String token) {
		this.token = token.getBytes(StandardCharsets.UTF_8);
	}

	boolean accepts(Map<String, Object> message) {
		if (!"auth".equals(message.get("op")) || !(message.get("args") instanceof Map<?, ?> args)) {
			return false;
		}
		return args.get("token") instanceof String offered
			&& MessageDigest.isEqual(token, offered.getBytes(StandardCharsets.UTF_8));
	}
}
