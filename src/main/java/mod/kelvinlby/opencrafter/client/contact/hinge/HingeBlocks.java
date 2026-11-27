package mod.kelvinlby.opencrafter.client.contact.hinge;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.Property;
import org.jspecify.annotations.Nullable;

import java.util.Set;

/** Which block changes are a hinged or pressed part switching state (and nothing else). */
final class HingeBlocks {
	private static final Set<Property<?>> TOGGLES = Set.of(BlockStateProperties.OPEN, BlockStateProperties.POWERED);

	private HingeBlocks() {
	}

	/** A door, trapdoor, fence gate, lever or button whose open/powered state is all that changed. */
	static boolean toggled(BlockState before, BlockState after) {
		return before.getBlock() == after.getBlock() && isHinged(before) && onlyTogglesDiffer(before, after);
	}

	/** The other half of a door, or null. */
	static @Nullable BlockPos otherHalf(BlockPos pos, BlockState state) {
		if (!(state.getBlock() instanceof DoorBlock)) {
			return null;
		}
		return state.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER ? pos.above() : pos.below();
	}

	/** {@code now} with the open state it had in {@code before} (the other half of a door, as it was). */
	static BlockState withOpenOf(BlockState now, BlockState before) {
		return now.setValue(BlockStateProperties.OPEN, before.getValue(BlockStateProperties.OPEN));
	}

	private static boolean isHinged(BlockState state) {
		return state.getBlock() instanceof DoorBlock || state.getBlock() instanceof TrapDoorBlock || state.getBlock() instanceof FenceGateBlock
			|| state.getBlock() instanceof LeverBlock || state.getBlock() instanceof ButtonBlock;
	}

	/**
	 * Only open/powered changed; a fence gate may also turn to face the player, because it opens
	 * away from them.
	 */
	private static boolean onlyTogglesDiffer(BlockState before, BlockState after) {
		boolean toggled = false;
		for (Property<?> property : before.getProperties()) {
			if (before.getValue(property).equals(after.getValue(property))) {
				continue;
			}
			if (TOGGLES.contains(property)) {
				toggled = true;
			} else if (!(property == FenceGateBlock.FACING && before.getBlock() instanceof FenceGateBlock)) {
				return false;
			}
		}
		return toggled;
	}
}
