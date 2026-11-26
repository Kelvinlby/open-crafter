package mod.kelvinlby.opencrafter.client.handview;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Placed blocks shown as what was there before until the hand's morph reaches them. Only this
 * client's copy of the world changes; whatever the server says about such a block meanwhile is kept
 * and applied when it is revealed, so a refused placement leaves no ghost block.
 */
final class HeldBackBlocks {
	/** Neighbour shapes stay as they are; the section is redrawn right away. */
	private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_IMMEDIATE | Block.UPDATE_KNOWN_SHAPE;
	/** A block this close below the player's feet may be what they stand on. */
	private static final double FOOTING = 0.6;

	private final Map<BlockPos, BlockState> real = new HashMap<>();
	private @Nullable ClientLevel level;

	/** Shows the block as it was before; skipped where hiding would matter for movement or a block entity. */
	void holdBack(ClientLevel level, LocalPlayer player, PlacedBlock placed) {
		if (level != this.level) {
			real.clear();
			this.level = level;
		}
		if (placed.state().hasBlockEntity() || supports(player, level, placed)) {
			return;
		}
		real.put(placed.pos(), placed.state());
		level.setBlock(placed.pos(), placed.before(), FLAGS);
	}

	/** The server set {@code pos} to {@code state}: kept for the reveal if the block is held back. */
	boolean intercept(BlockPos pos, BlockState state) {
		if (!real.containsKey(pos)) {
			return false;
		}
		real.put(pos.immutable(), state);
		return true;
	}

	/** Reveals every held-back block except those at {@code keep}. */
	void revealAllBut(Set<BlockPos> keep) {
		if (level == null || real.isEmpty()) {
			return;
		}
		real.entrySet().removeIf(entry -> !keep.contains(entry.getKey()) && reveal(entry.getKey(), entry.getValue()));
	}

	private boolean reveal(BlockPos pos, BlockState state) {
		if (level != null) {
			level.setBlock(pos, state, FLAGS);
		}
		return true;
	}

	private static boolean supports(LocalPlayer player, ClientLevel level, PlacedBlock placed) {
		VoxelShape shape = placed.state().getCollisionShape(level, placed.pos());
		if (shape.isEmpty()) {
			return false;
		}
		AABB reach = player.getBoundingBox().expandTowards(0, -FOOTING, 0);
		return shape.bounds().move(placed.pos()).intersects(reach);
	}
}
