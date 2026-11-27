package mod.kelvinlby.opencrafter.client.contact.crack;

import mod.kelvinlby.opencrafter.client.interaction.event.InteractionEvent;
import mod.kelvinlby.opencrafter.client.interaction.event.InteractionKind;
import mod.kelvinlby.opencrafter.client.interaction.event.InteractionListener;
import mod.kelvinlby.opencrafter.client.interaction.event.InteractionTarget;
import mod.kelvinlby.opencrafter.core.config.ConfigStore;
import mod.kelvinlby.opencrafter.core.config.ContactEffectsConfig;
import mod.kelvinlby.opencrafter.mixin.contact.MultiPlayerGameModeAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * The game only cracks a block while the player's own keyboard break runs ({@code
 * MultiPlayerGameMode.continueDestroyBlock}). This follows every break the interaction layer
 * sees, from {@code break_start} to {@code break_done}/{@code break_abort} (also raw packets from
 * other mods), and shows the same cracks through the game's own {@code destroyBlockProgress},
 * at the game's rate for that block and the held tool. While the game's own break runs on the
 * block it is left to show its cracks alone.
 */
public final class CrackOverlay implements InteractionListener {
	/** Breaker id of these cracks in the level's table; entity ids are never this low. */
	static final int BREAKER_ID = Integer.MIN_VALUE + 0x4F43;

	private final ConfigStore config;
	private @Nullable BreakSession session;
	private @Nullable ClientLevel shownIn;

	public CrackOverlay(ConfigStore config) {
		this.config = config;
	}

	@Override
	public void onInteraction(InteractionEvent event) {
		if (!(event.target() instanceof InteractionTarget.Block block)) {
			return;
		}
		if (event.kind() == InteractionKind.BREAK_START) {
			start(block.pos());
		} else if (isEnd(event.kind()) && session != null && session.pos.equals(block.pos())) {
			stop();
		}
	}

	/** Once per tick, after the game's own breaking ran. */
	public void tick() {
		Minecraft minecraft = Minecraft.getInstance();
		ClientLevel level = minecraft.level;
		LocalPlayer player = minecraft.player;
		BreakSession current = session;
		if (current == null || level == null || player == null) {
			return;
		}
		if (shouldEnd(current, level)) {
			stop();
			return;
		}
		current.advance(current.state.getDestroyProgress(player, level, current.pos));
		show(level, current, minecraft.gameMode);
	}

	/** Leaving a world or switching the effect off. */
	public void stop() {
		BreakSession ended = session;
		session = null;
		if (ended != null) {
			clear(ended.pos);
		}
	}

	private void start(BlockPos pos) {
		stop();
		Minecraft minecraft = Minecraft.getInstance();
		ClientLevel level = minecraft.level;
		LocalPlayer player = minecraft.player;
		if (!config.get().contactEffects.crackOverlay || level == null || player == null) {
			return;
		}
		BlockState state = level.getBlockState(pos);
		if (!state.isAir() && state.getDestroyProgress(player, level, pos) < 1.0F) {
			session = new BreakSession(pos, state);
			shownIn = level;
		}
	}

	private boolean shouldEnd(BreakSession current, ClientLevel level) {
		ContactEffectsConfig settings = config.get().contactEffects;
		return !settings.crackOverlay || level != shownIn || !level.getBlockState(current.pos).equals(current.state)
			|| current.ticksBroken() > Math.max(0, settings.crackLingerTicks);
	}

	private static void show(ClientLevel level, BreakSession current, @Nullable MultiPlayerGameMode gameMode) {
		int stage = gameShowsCracks(gameMode, current.pos) ? -1 : current.stage();
		level.destroyBlockProgress(BREAKER_ID, current.pos, stage);
	}

	/** The game's own break is running on this block: it shows the cracks itself. */
	private static boolean gameShowsCracks(@Nullable MultiPlayerGameMode gameMode, BlockPos pos) {
		return gameMode != null && gameMode.isDestroying() && pos.equals(((MultiPlayerGameModeAccessor) gameMode).openCrafter$destroyBlockPos());
	}

	private void clear(BlockPos pos) {
		ClientLevel level = shownIn;
		if (level != null && level == Minecraft.getInstance().level) {
			level.destroyBlockProgress(BREAKER_ID, pos, -1);
		}
	}

	private static boolean isEnd(InteractionKind kind) {
		return kind == InteractionKind.BREAK_DONE || kind == InteractionKind.BREAK_ABORT;
	}
}
