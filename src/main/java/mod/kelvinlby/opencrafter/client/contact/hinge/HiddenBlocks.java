package mod.kelvinlby.opencrafter.client.contact.hinge;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashSet;
import java.util.Set;

/**
 * Blocks left out of the chunk mesh while a temporary model is drawn in their place. Purely
 * visual: only the chunk mesher (vanilla's and Sodium's) is told there is air; the world,
 * collisions and clicks keep the real block.
 */
public final class HiddenBlocks {
	private static final BlockState NOTHING = Blocks.AIR.defaultBlockState();

	/** Replaced whole on every change; read from mesher threads. */
	private volatile Set<Long> hidden = Set.of();

	void hide(ClientLevel level, BlockPos pos) {
		Set<Long> next = new HashSet<>(hidden);
		if (next.add(pos.asLong())) {
			hidden = Set.copyOf(next);
			redraw(level, pos);
		}
	}

	void reveal(ClientLevel level, BlockPos pos) {
		Set<Long> next = new HashSet<>(hidden);
		if (next.remove(pos.asLong())) {
			hidden = Set.copyOf(next);
			redraw(level, pos);
		}
	}

	/** Leaving the world: nothing to redraw. */
	public void forget() {
		hidden = Set.of();
	}

	/** The state the mesher should draw at a position, given the real one (any thread). */
	public BlockState meshed(int x, int y, int z, BlockState real) {
		Set<Long> current = hidden;
		return current.isEmpty() || !current.contains(BlockPos.asLong(x, y, z)) ? real : NOTHING;
	}

	/** Rebuilds the section around {@code pos} right away (vanilla and Sodium both listen); the world is unchanged. */
	private static void redraw(ClientLevel level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		level.sendBlockUpdated(pos, state, state, Block.UPDATE_IMMEDIATE);
	}
}
