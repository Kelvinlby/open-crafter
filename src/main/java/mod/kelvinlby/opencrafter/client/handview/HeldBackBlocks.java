package mod.kelvinlby.opencrafter.client.handview;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Placed blocks drawn as what was there before until the hand's morph reaches them. Purely visual:
 * only the chunk mesher is told the old state (vanilla's and Sodium's); the world, collisions and
 * clicks keep the real block.
 */
final class HeldBackBlocks {
	/** Position → the state the mesher draws there instead; replaced whole, read from mesher threads. */
	private volatile Map<Long, BlockState> drawnInstead = Map.of();
	private @Nullable ClientLevel level;

	/** Draws the placed block as it was before; skipped when a block entity renderer would draw it anyway. */
	void holdBack(ClientLevel level, PlacedBlock placed) {
		if (level != this.level) {
			drawnInstead = Map.of();
			this.level = level;
		}
		if (drawnByBlockEntity(level, placed.pos())) {
			return;
		}
		Map<Long, BlockState> next = new HashMap<>(drawnInstead);
		next.put(placed.pos().asLong(), placed.before());
		drawnInstead = Map.copyOf(next);
		redraw(level, placed.pos());
	}

	/** The state the mesher should draw at a position, given the real one (any thread). */
	BlockState meshed(int x, int y, int z, BlockState real) {
		Map<Long, BlockState> instead = drawnInstead;
		if (instead.isEmpty()) {
			return real;
		}
		BlockState state = instead.get(BlockPos.asLong(x, y, z));
		return state == null ? real : state;
	}

	/** Draws every held-back block as it really is again, except those at {@code keep}. */
	void revealAllBut(Set<BlockPos> keep) {
		Map<Long, BlockState> instead = drawnInstead;
		if (instead.isEmpty() || level == null) {
			return;
		}
		Map<Long, BlockState> next = new HashMap<>();
		for (Map.Entry<Long, BlockState> entry : instead.entrySet()) {
			BlockPos pos = BlockPos.of(entry.getKey());
			if (keep.contains(pos)) {
				next.put(entry.getKey(), entry.getValue());
			}
		}
		drawnInstead = Map.copyOf(next);
		for (Long pos : instead.keySet()) {
			if (!next.containsKey(pos)) {
				redraw(level, BlockPos.of(pos));
			}
		}
	}

	/** Rebuilds the sections around {@code pos} right away (vanilla and Sodium both listen); the world is unchanged. */
	private static void redraw(ClientLevel level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		level.sendBlockUpdated(pos, state, state, Block.UPDATE_IMMEDIATE);
	}

	private static boolean drawnByBlockEntity(ClientLevel level, BlockPos pos) {
		BlockEntity blockEntity = level.getBlockEntity(pos);
		return blockEntity != null && Minecraft.getInstance().getBlockEntityRenderDispatcher().getRenderer(blockEntity) != null;
	}
}
