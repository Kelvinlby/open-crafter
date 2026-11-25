package mod.kelvinlby.opencrafter.transport;

import mod.kelvinlby.opencrafter.api.EventSink;
import mod.kelvinlby.opencrafter.api.frame.FramePublisher;
import mod.kelvinlby.opencrafter.api.frame.FrameSlot;
import mod.kelvinlby.opencrafter.api.frame.StreamSpec;
import mod.kelvinlby.opencrafter.api.handler.HandlerRegistry;
import mod.kelvinlby.opencrafter.core.config.TransportConfig;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

/**
 * Shared-memory frame ring + Unix-domain-socket request server.
 * Pure Java: must not reference Minecraft classes.
 */
public final class TransportModule implements AutoCloseable {
	// TODO implemented by the transport work stream; these stubs keep the build green.
	public static TransportModule start(TransportConfig config, HandlerRegistry handlers, Executor mainThread) {
		return new TransportModule();
	}

	public FramePublisher frames() {
		return new FramePublisher() {
			@Override
			public boolean hasReaders() {
				return false;
			}

			@Override
			public FrameSlot acquire(List<StreamSpec> streams) {
				return null;
			}
		};
	}

	public EventSink events() {
		return (String event, Map<String, Object> payload) -> {
		};
	}

	@Override
	public void close() {
	}
}
