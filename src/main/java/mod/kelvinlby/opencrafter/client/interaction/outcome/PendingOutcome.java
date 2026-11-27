package mod.kelvinlby.opencrafter.client.interaction.outcome;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import mod.kelvinlby.opencrafter.client.interaction.event.InteractionEvent;
import mod.kelvinlby.opencrafter.client.interaction.event.Outcome;
import org.jspecify.annotations.Nullable;

import java.util.List;

/** One action waiting for the world to confirm it. */
final class PendingOutcome {
	final InteractionEvent event;
	private final List<OutcomeProbe> probes;
	private final IntSet hurtEntities = new IntOpenHashSet();
	private final long deadlineNanos;

	PendingOutcome(InteractionEvent event, List<OutcomeProbe> probes, long deadlineNanos) {
		this.event = event;
		this.probes = probes;
		this.deadlineNanos = deadlineNanos;
	}

	void noteHurt(int entityId) {
		hurtEntities.add(entityId);
	}

	/** The first probe that confirms something, or null. */
	@Nullable Outcome probe(ProbeContext.Base base) {
		ProbeContext context = base.with(hurtEntities);
		for (OutcomeProbe probe : probes) {
			Outcome outcome = probe.probe(context);
			if (outcome != null) {
				return outcome;
			}
		}
		return null;
	}

	boolean hasProbes() {
		return !probes.isEmpty();
	}

	/** The window has run out ({@code Util.getNanos()}, the game clock clock). */
	boolean expiredAt(long nowNanos) {
		return nowNanos - deadlineNanos >= 0;
	}
}
