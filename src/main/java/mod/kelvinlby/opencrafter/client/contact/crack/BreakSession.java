package mod.kelvinlby.opencrafter.client.contact.crack;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/** A break in progress on one block, counted the way the game counts the player's own break. */
final class BreakSession {
	/** The game shows ten crack stages, 0 to 9. */
	private static final int LAST_STAGE = 9;

	final BlockPos pos;
	final BlockState state;
	private float progress;
	private int ticksBroken;

	BreakSession(BlockPos pos, BlockState state) {
		this.pos = pos.immutable();
		this.state = state;
	}

	/** One tick of breaking at {@code perTick} (the block's destroy progress for the held tool). */
	void advance(float perTick) {
		progress += perTick;
		if (progress >= 1.0F) {
			ticksBroken++;
		}
	}

	/** The crack stage to show, -1 for none yet (as the game's own {@code getDestroyStage}). */
	int stage() {
		return progress > 0.0F ? Math.min(LAST_STAGE, (int) (progress * 10.0F)) : -1;
	}

	/** Ticks since the block should have broken. */
	int ticksBroken() {
		return ticksBroken;
	}
}
