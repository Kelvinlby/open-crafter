package mod.kelvinlby.opencrafter.client.handview;

import mod.kelvinlby.opencrafter.client.interaction.event.BlockChange;
import mod.kelvinlby.opencrafter.client.interaction.event.InteractionEvent;
import mod.kelvinlby.opencrafter.client.interaction.event.InteractionTarget;
import mod.kelvinlby.opencrafter.client.interaction.event.Outcome;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.Blocks;
import org.jspecify.annotations.Nullable;

/** Tells from a right click on a block and its outcome whether a block was placed, and where. */
final class Placements {
	private Placements() {
	}

	/**
	 * The placed block, or null if nothing was placed. A block is placed next to the clicked face,
	 * replaces the clicked block (grass, snow), or joins it (a slab made double); a clicked block of
	 * another kind that only changed its state (a door opening) was interacted with.
	 */
	static @Nullable PlacedBlock placed(InteractionEvent event, Outcome outcome) {
		if (!(event.target() instanceof InteractionTarget.Block target) || event.hand() == null
			|| !(event.held().getItem() instanceof BlockItem item)) {
			return null;
		}
		BlockPos clicked = target.pos();
		BlockPos adjacent = clicked.relative(target.face());
		BlockChange next = changeAt(outcome, adjacent);
		if (next != null) {
			return new PlacedBlock(adjacent, next.after(), next.before(), event.held(), event.hand());
		}
		BlockChange onClicked = changeAt(outcome, clicked);
		if (onClicked == null) {
			return null;
		}
		if (!onClicked.after().is(onClicked.before().getBlock())) {
			return new PlacedBlock(clicked, onClicked.after(), Blocks.AIR.defaultBlockState(), event.held(), event.hand());
		}
		if (onClicked.after().is(item.getBlock()) && !onClicked.after().equals(onClicked.before())) {
			return new PlacedBlock(clicked, onClicked.after(), onClicked.before(), event.held(), event.hand());
		}
		return null;
	}

	private static @Nullable BlockChange changeAt(Outcome outcome, BlockPos pos) {
		for (BlockChange change : outcome.blockChanges()) {
			if (change.pos().equals(pos) && !change.after().equals(change.before())) {
				return change;
			}
		}
		return null;
	}
}
