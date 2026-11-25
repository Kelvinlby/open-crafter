package mod.kelvinlby.opencrafter.transport.rpc;

import java.util.Map;

/**
 * One parsed control message. A {@code null} id marks a fire-and-forget request.
 * The id is echoed back verbatim (clients should use integers).
 */
public record Request(Object id, String op, Map<String, Object> args) {
	public boolean expectsReply() {
		return id != null;
	}

	/** @throws IllegalArgumentException when op or args are malformed */
	public static Request parse(Map<String, Object> message) {
		return new Request(message.get("id"), parseOp(message.get("op")), parseArgs(message.get("args")));
	}

	static String parseOp(Object op) {
		if (!(op instanceof String name) || name.isEmpty()) {
			throw new IllegalArgumentException("Request needs a non-empty string 'op'");
		}
		return name;
	}

	@SuppressWarnings("unchecked")
	static Map<String, Object> parseArgs(Object args) {
		if (args == null) {
			return Map.of();
		}
		if (!(args instanceof Map<?, ?> map)) {
			throw new IllegalArgumentException("'args' must be a map");
		}
		return (Map<String, Object>) map;
	}
}
