package mod.kelvinlby.opencrafter.client.handview;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.item.ItemStack;

/**
 * A block a right click placed: where, what it is now, what was there before (air, or the slab it
 * joined), and the stack that placed it as it was before the click.
 */
record PlacedBlock(BlockPos pos, BlockState state, BlockState before, ItemStack held) {
}
