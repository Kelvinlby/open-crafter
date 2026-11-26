package mod.kelvinlby.opencrafter.client.handview;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** Tells, around one right click on a block, whether a block was placed and where. */
final class PlacementProbe {
	private final BlockPos clicked;
	private final BlockPos adjacent;
	private final BlockState clickedBefore;
	private final BlockState adjacentBefore;

	private PlacementProbe(ClientLevel level, BlockHitResult hit) {
		this.clicked = hit.getBlockPos();
		this.adjacent = clicked.relative(hit.getDirection());
		this.clickedBefore = level.getBlockState(clicked);
		this.adjacentBefore = level.getBlockState(adjacent);
	}

	/** Null when the held item cannot place a block, so the click can only interact. */
	static @Nullable PlacementProbe before(ClientLevel level, ItemStack held, BlockHitResult hit) {
		return held.getItem() instanceof BlockItem ? new PlacementProbe(level, hit) : null;
	}

	/**
	 * The centre of the placed block, or null if nothing was placed. A block is placed next to the
	 * clicked face, or replaces the clicked block (grass, snow); a clicked block that only changed
	 * its state (a door opening) was interacted with.
	 */
	@Nullable Vec3 placedCentre(ClientLevel level) {
		if (!level.getBlockState(adjacent).equals(adjacentBefore)) {
			return Vec3.atCenterOf(adjacent);
		}
		if (!level.getBlockState(clicked).is(clickedBefore.getBlock())) {
			return Vec3.atCenterOf(clicked);
		}
		return null;
	}
}
