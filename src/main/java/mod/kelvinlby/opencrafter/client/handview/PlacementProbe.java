package mod.kelvinlby.opencrafter.client.handview;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.Nullable;

/** Tells, around one right click on a block, whether a block was placed and where. */
final class PlacementProbe {
	private final ItemStack heldStack;
	private final InteractionHand hand;
	private final Block held;
	private final BlockPos clicked;
	private final BlockPos adjacent;
	private final BlockState clickedBefore;
	private final BlockState adjacentBefore;

	private PlacementProbe(ClientLevel level, ItemStack heldStack, InteractionHand hand, Block held, BlockHitResult hit) {
		this.heldStack = heldStack.copy();
		this.hand = hand;
		this.held = held;
		this.clicked = hit.getBlockPos();
		this.adjacent = clicked.relative(hit.getDirection());
		this.clickedBefore = level.getBlockState(clicked);
		this.adjacentBefore = level.getBlockState(adjacent);
	}

	/** Null when the held item cannot place a block, so the click can only interact. */
	static @Nullable PlacementProbe before(ClientLevel level, ItemStack held, InteractionHand hand, BlockHitResult hit) {
		return held.getItem() instanceof BlockItem item ? new PlacementProbe(level, held, hand, item.getBlock(), hit) : null;
	}

	/**
	 * The placed block, or null if nothing was placed. A block is placed next to the clicked face,
	 * replaces the clicked block (grass, snow), or joins it (a slab made double); a clicked block of
	 * another kind that only changed its state (a door opening) was interacted with.
	 */
	@Nullable PlacedBlock placed(ClientLevel level) {
		BlockState adjacentNow = level.getBlockState(adjacent);
		if (!adjacentNow.equals(adjacentBefore)) {
			return new PlacedBlock(adjacent, adjacentNow, adjacentBefore, heldStack, hand);
		}
		BlockState clickedNow = level.getBlockState(clicked);
		if (!clickedNow.is(clickedBefore.getBlock())) {
			return new PlacedBlock(clicked, clickedNow, Blocks.AIR.defaultBlockState(), heldStack, hand);
		}
		if (clickedNow.is(held) && !clickedNow.equals(clickedBefore)) {
			return new PlacedBlock(clicked, clickedNow, clickedBefore, heldStack, hand);
		}
		return null;
	}
}
