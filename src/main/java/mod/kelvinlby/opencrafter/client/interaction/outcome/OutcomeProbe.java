package mod.kelvinlby.opencrafter.client.interaction.outcome;

import mod.kelvinlby.opencrafter.client.interaction.event.Outcome;
import org.jspecify.annotations.Nullable;

/** One way the world can confirm an action. */
@FunctionalInterface
interface OutcomeProbe {
	/** The confirmed outcome, or null while this probe has seen nothing yet. */
	@Nullable Outcome probe(ProbeContext context);
}
