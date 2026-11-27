package mod.kelvinlby.opencrafter.client.interaction.event;

/** Receives interactions on the client thread, in the order they were sent. */
public interface InteractionListener {
	/** Right after the action was sent; for game-mode actions, after the client's own prediction ran. */
	default void onInteraction(InteractionEvent event) {
	}

	/** Once per event, when its outcome is decided (possibly in the same tick it was sent). */
	default void onOutcome(InteractionEvent event, Outcome outcome) {
	}
}
