package mod.kelvinlby.opencrafter.client.interaction.outcome;

import mod.kelvinlby.opencrafter.client.interaction.event.InteractionBus;
import mod.kelvinlby.opencrafter.client.interaction.event.InteractionEvent;
import mod.kelvinlby.opencrafter.client.interaction.event.Outcome;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Util;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.LongSupplier;

/**
 * Decides each action's outcome: an effect as soon as the world confirms one, {@code none} when
 * the window runs out first. Every event gets exactly one outcome. Client thread only.
 */
public final class OutcomeResolver {
	private final InteractionBus bus;
	private final LongSupplier windowNanos;
	private final List<PendingOutcome> pending = new ArrayList<>();

	public OutcomeResolver(InteractionBus bus, LongSupplier windowNanos) {
		this.bus = bus;
		this.windowNanos = windowNanos;
	}

	/** Starts waiting for this event's outcome; an action that cannot change anything resolves at once. */
	public void track(InteractionEvent event, PreAction before, LocalPlayer player) {
		PendingOutcome entry = new PendingOutcome(event, ProbeSets.forEvent(event, before, player), event.nanos() + windowNanos.getAsLong());
		if (entry.hasProbes()) {
			pending.add(entry);
		} else {
			bus.publishOutcome(event, Outcome.NONE);
		}
	}

	public void onEntityHurt(int entityId) {
		pending.forEach(entry -> entry.noteHurt(entityId));
		checkNow();
	}

	/** Resolves every action the world has confirmed by now. */
	public void checkNow() {
		ProbeContext.Base base = base();
		if (base != null) {
			publishAll(takeConfirmed(base));
		}
	}

	/** End of every client tick: checks again, then times out what waited too long. */
	public void tick() {
		checkNow();
		publishAll(takeExpired(Util.getNanos()));
	}

	private List<Resolved> takeConfirmed(ProbeContext.Base base) {
		List<Resolved> resolved = new ArrayList<>();
		Iterator<PendingOutcome> entries = pending.iterator();
		while (entries.hasNext()) {
			PendingOutcome entry = entries.next();
			Outcome outcome = entry.probe(base);
			if (outcome != null) {
				entries.remove();
				resolved.add(new Resolved(entry.event, outcome));
			}
		}
		return resolved;
	}

	private List<Resolved> takeExpired(long now) {
		List<Resolved> resolved = new ArrayList<>();
		Iterator<PendingOutcome> entries = pending.iterator();
		while (entries.hasNext()) {
			PendingOutcome entry = entries.next();
			if (entry.expiredAt(now)) {
				entries.remove();
				resolved.add(new Resolved(entry.event, Outcome.NONE));
			}
		}
		return resolved;
	}

	/** Published after the list was updated, so a listener may safely cause new actions. */
	private void publishAll(List<Resolved> resolved) {
		resolved.forEach(entry -> bus.publishOutcome(entry.event(), entry.outcome()));
	}

	/** Leaving a world: whatever waits can no longer be confirmed. */
	public void clear() {
		List<Resolved> dropped = pending.stream().map(entry -> new Resolved(entry.event, Outcome.NONE)).toList();
		pending.clear();
		publishAll(dropped);
	}

	private static ProbeContext.@Nullable Base base() {
		Minecraft minecraft = Minecraft.getInstance();
		LocalPlayer player = minecraft.player;
		ClientLevel level = minecraft.level;
		return player == null || level == null ? null : new ProbeContext.Base(minecraft, player, level);
	}

	private record Resolved(InteractionEvent event, Outcome outcome) {
	}
}
