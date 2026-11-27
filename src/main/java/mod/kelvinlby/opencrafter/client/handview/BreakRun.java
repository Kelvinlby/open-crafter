package mod.kelvinlby.opencrafter.client.handview;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import org.jspecify.annotations.Nullable;

/**
 * The block the player is breaking: from {@code break_start} until {@code break_done},
 * {@code break_abort} or the block is gone (instant breaks send no end). Mining swings carry on
 * while it runs, and a little after it ended, through vanilla's delay before the next block.
 */
final class BreakRun {
	/** Vanilla waits 5 ticks between broken blocks while still swinging. */
	private static final long LINGER_TICKS = 6;

	private @Nullable BlockPos pos;
	private InteractionHand hand = InteractionHand.MAIN_HAND;
	private long endedAt = Long.MIN_VALUE / 2;

	/** {@code shownHand}: the hand the mining swings show in. */
	void start(BlockPos block, InteractionHand shownHand) {
		pos = block;
		hand = shownHand;
	}

	void stop(long now) {
		if (pos != null) {
			pos = null;
			endedAt = now;
		}
	}

	/** Once per tick: a block that is gone ends its break. */
	void tick(@Nullable ClientLevel level, long now) {
		if (pos != null && (level == null || level.getBlockState(pos).isAir())) {
			stop(now);
		}
	}

	void reset() {
		pos = null;
		endedAt = Long.MIN_VALUE / 2;
	}

	boolean running() {
		return pos != null;
	}

	InteractionHand hand() {
		return hand;
	}

	/** Swings of {@code shownHand} belong to the break: it runs or just ended. */
	boolean claims(InteractionHand shownHand, long now) {
		return shownHand == hand && (pos != null || now - endedAt <= LINGER_TICKS);
	}
}
