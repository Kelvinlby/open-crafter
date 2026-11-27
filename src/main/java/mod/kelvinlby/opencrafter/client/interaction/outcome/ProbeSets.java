package mod.kelvinlby.opencrafter.client.interaction.outcome;

import mod.kelvinlby.opencrafter.client.interaction.event.InteractionEvent;
import mod.kelvinlby.opencrafter.client.interaction.event.InteractionTarget;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/** Which consequences confirm which kind of action. */
final class ProbeSets {
	private ProbeSets() {
	}

	static List<OutcomeProbe> forEvent(InteractionEvent event, PreAction before, LocalPlayer player) {
		List<OutcomeProbe> probes = new ArrayList<>();
		switch (event.kind()) {
			case USE_ON_BLOCK -> {
				probes.add(Probes.blocksChanged(PreAction.watchedFor(event.target()), before));
				probes.add(Probes.screenOpened(before));
				addHeld(probes, event.hand(), before);
			}
			case BREAK_START, BREAK_DONE -> probes.add(Probes.blocksChanged(PreAction.watchedFor(event.target()), before));
			case ATTACK_ENTITY -> addEntity(probes, event.target(), false);
			case INTERACT_ENTITY -> {
				addEntity(probes, event.target(), true);
				probes.add(Probes.screenOpened(before));
				probes.add(Probes.vehicleChanged(before));
				addHeld(probes, event.hand(), before);
			}
			case STAB -> probes.add(Probes.stabHit(player.getEyePosition(), event.aim(), player.entityInteractionRange()));
			case USE_ITEM -> {
				probes.add(Probes.startedUsing(before));
				probes.add(Probes.screenOpened(before));
				addHeld(probes, event.hand(), before);
			}
			case RELEASE_USE -> {
				addHeld(probes, InteractionHand.MAIN_HAND, before);
				addHeld(probes, InteractionHand.OFF_HAND, before);
			}
			case BREAK_ABORT -> {
				// Stopping a break changes nothing in the world.
			}
		}
		return probes;
	}

	private static void addHeld(List<OutcomeProbe> probes, @Nullable InteractionHand hand, PreAction before) {
		if (hand != null) {
			probes.add(Probes.heldItemChanged(hand, before));
		}
	}

	private static void addEntity(List<OutcomeProbe> probes, InteractionTarget target, boolean interact) {
		if (target instanceof InteractionTarget.Entity entity) {
			if (!interact) {
				probes.add(Probes.entityHurt(entity.entityId()));
			}
			probes.add(Probes.entityGone(entity.entityId()));
		}
	}
}
