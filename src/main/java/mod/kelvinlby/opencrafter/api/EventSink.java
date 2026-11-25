package mod.kelvinlby.opencrafter.api;

import java.util.Map;

/** Pushes unsolicited events to every connected backend. Thread-safe and non-blocking. */
public interface EventSink {
	void publish(String event, Map<String, Object> payload);
}
