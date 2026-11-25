package mod.kelvinlby.opencrafter.transport;

import mod.kelvinlby.opencrafter.api.handler.HandlerRegistry;
import mod.kelvinlby.opencrafter.transport.wire.WireFormat;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * The {@code hello} event sent first on every connection. Built per connection because
 * modules register their ops after the transport has started.
 */
final class Hello {
	private final HandlerRegistry handlers;
	private final Supplier<Path> ringPath;

	Hello(HandlerRegistry handlers, Supplier<Path> ringPath) {
		this.handlers = handlers;
		this.ringPath = ringPath;
	}

	byte[] encode() {
		return WireFormat.event("hello", data());
	}

	Map<String, Object> data() {
		Path ring = ringPath.get();
		Map<String, Object> data = new LinkedHashMap<>();
		data.put("protocol", WireFormat.PROTOCOL_VERSION);
		data.put("shm", ring == null ? null : ring.toString());
		data.put("ops", sortedOps());
		data.put("pid", ProcessHandle.current().pid());
		return data;
	}

	private List<String> sortedOps() {
		return handlers.all().stream().map(HandlerRegistry.Entry::op).sorted().toList();
	}
}
