package mod.kelvinlby.opencrafter.client.interaction.event;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** Fans interactions and their outcomes out to every listener. Client thread only. */
public final class InteractionBus {
	private final List<InteractionListener> listeners = new CopyOnWriteArrayList<>();

	public void subscribe(InteractionListener listener) {
		listeners.add(listener);
	}

	public void publish(InteractionEvent event) {
		listeners.forEach(listener -> listener.onInteraction(event));
	}

	public void publishOutcome(InteractionEvent event, Outcome outcome) {
		listeners.forEach(listener -> listener.onOutcome(event, outcome));
	}
}
