package mod.kelvinlby.opencrafter.api.handler;

import mod.kelvinlby.opencrafter.api.ThreadAffinity;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** Op name to handler table; filled by modules at startup, read by the transport. */
public final class HandlerRegistry {
	public record Entry(String op, ThreadAffinity affinity, RequestHandler handler) {
	}

	private final Map<String, Entry> entries = new ConcurrentHashMap<>();

	public void register(String op, ThreadAffinity affinity, RequestHandler handler) {
		Entry previous = entries.putIfAbsent(op, new Entry(op, affinity, handler));
		if (previous != null) {
			throw new IllegalStateException("Duplicate op: " + op);
		}
	}

	public Optional<Entry> find(String op) {
		return Optional.ofNullable(entries.get(op));
	}

	public Collection<Entry> all() {
		return entries.values();
	}
}
