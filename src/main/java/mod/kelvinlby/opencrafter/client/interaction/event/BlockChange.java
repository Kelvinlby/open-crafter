package mod.kelvinlby.opencrafter.client.interaction.event;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/** A block that changed because of an interaction. */
public record BlockChange(BlockPos pos, BlockState before, BlockState after) {
}
