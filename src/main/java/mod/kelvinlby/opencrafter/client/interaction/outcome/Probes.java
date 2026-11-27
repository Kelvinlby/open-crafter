package mod.kelvinlby.opencrafter.client.interaction.outcome;

import mod.kelvinlby.opencrafter.client.interaction.event.Aim;
import mod.kelvinlby.opencrafter.client.interaction.event.BlockChange;
import mod.kelvinlby.opencrafter.client.interaction.event.Outcome;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/** The individual probes. Each checks one kind of consequence. */
final class Probes {
	private static final double STAB_RAY_SLACK = 0.3;

	private Probes() {
	}

	/** Any watched block differs from its state before the action. */
	static OutcomeProbe blocksChanged(List<BlockPos> watched, PreAction before) {
		return context -> {
			List<BlockChange> changes = new ArrayList<>();
			for (BlockPos pos : watched) {
				BlockState was = before.blockBefore(context.level(), pos);
				BlockState now = context.level().getBlockState(pos);
				if (!now.equals(was)) {
					changes.add(new BlockChange(pos, was, now));
				}
			}
			return changes.isEmpty() ? null : Outcome.blocks(changes);
		};
	}

	/** A screen opened that was not open before (a container, a trade, a sign editor). */
	static OutcomeProbe screenOpened(PreAction before) {
		return context -> !before.screenOpen() && context.minecraft().gui.screen() != null ? Outcome.of("screen_opened") : null;
	}

	/** What the hand holds changed (consumed, filled, damaged, transformed). */
	static OutcomeProbe heldItemChanged(InteractionHand hand, PreAction before) {
		ItemStack was = before.held(hand);
		return context -> ItemStack.matches(was, context.player().getItemInHand(hand)) ? null : Outcome.of("held_item_changed");
	}

	/** The player started using the item (eating, drawing a bow, raising a shield). */
	static OutcomeProbe startedUsing(PreAction before) {
		return context -> !before.usingItem() && context.player().isUsingItem() ? Outcome.of("item_in_use") : null;
	}

	/** The player mounted or dismounted something. */
	static OutcomeProbe vehicleChanged(PreAction before) {
		return context -> context.player().getVehicle() != before.vehicle() ? Outcome.of("vehicle_changed") : null;
	}

	/** The server reported the entity hurt. */
	static OutcomeProbe entityHurt(int entityId) {
		return context -> context.hurtEntities().contains(entityId) ? Outcome.of("entity_hurt") : null;
	}

	/** The entity is gone (broken item frame, popped armour stand, picked-up boat). */
	static OutcomeProbe entityGone(int entityId) {
		return context -> {
			Entity entity = context.level().getEntity(entityId);
			return entity == null || entity.isRemoved() ? Outcome.of("entity_removed") : null;
		};
	}

	/** An entity whose box the stab ray crosses was reported hurt. */
	static OutcomeProbe stabHit(Vec3 eye, Aim aim, double reach) {
		Vec3 end = eye.add(aim.direction().scale(reach));
		return context -> {
			for (int id : context.hurtEntities()) {
				Entity entity = context.level().getEntity(id);
				if (entity != null && crosses(entity.getBoundingBox(), eye, end)) {
					return Outcome.of("entity_hurt");
				}
			}
			return null;
		};
	}

	private static boolean crosses(AABB box, Vec3 from, Vec3 to) {
		return box.inflate(STAB_RAY_SLACK).clip(from, to).isPresent();
	}
}
