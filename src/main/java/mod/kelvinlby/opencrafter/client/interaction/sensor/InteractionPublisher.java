package mod.kelvinlby.opencrafter.client.interaction.sensor;

import mod.kelvinlby.opencrafter.api.EventSink;
import mod.kelvinlby.opencrafter.client.interaction.event.InteractionEvent;
import mod.kelvinlby.opencrafter.client.interaction.event.InteractionListener;
import mod.kelvinlby.opencrafter.client.interaction.event.Outcome;

/** Sends every interaction and outcome to connected backends as {@code interaction} / {@code interaction.outcome} events. */
public final class InteractionPublisher implements InteractionListener {
	private final EventSink events;

	public InteractionPublisher(EventSink events) {
		this.events = events;
	}

	@Override
	public void onInteraction(InteractionEvent event) {
		events.publish("interaction", InteractionMaps.event(event));
	}

	@Override
	public void onOutcome(InteractionEvent event, Outcome outcome) {
		events.publish("interaction.outcome", InteractionMaps.outcome(event, outcome));
	}
}
