package mod.kelvinlby.opencrafter.client.contact.shrink;

import mod.kelvinlby.opencrafter.client.contact.EffectClock;
import mod.kelvinlby.opencrafter.client.contact.render.TempBlocks;
import mod.kelvinlby.opencrafter.client.interaction.event.BlockChange;
import mod.kelvinlby.opencrafter.client.interaction.event.InteractionEvent;
import mod.kelvinlby.opencrafter.client.interaction.event.InteractionKind;
import mod.kelvinlby.opencrafter.client.interaction.event.InteractionListener;
import mod.kelvinlby.opencrafter.client.interaction.event.Outcome;
import mod.kelvinlby.opencrafter.core.config.ConfigStore;
import mod.kelvinlby.opencrafter.core.config.ContactEffectsConfig;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;

/** A confirmed break: the removed block shrinks into its drop instead of vanishing in one frame. */
public final class BreakShrink implements InteractionListener {
	private final ConfigStore config;
	private final TempBlocks blocks;
	private final EffectClock clock;

	public BreakShrink(ConfigStore config, TempBlocks blocks, EffectClock clock) {
		this.config = config;
		this.blocks = blocks;
		this.clock = clock;
	}

	@Override
	public void onOutcome(InteractionEvent event, Outcome outcome) {
		ContactEffectsConfig settings = config.get().contactEffects;
		if (!settings.breakShrink || !outcome.effect() || !isBreak(event.kind())) {
			return;
		}
		for (BlockChange change : outcome.blockChanges()) {
			if (removed(change)) {
				blocks.add(new ShrinkingBlock(change.pos(), change.before(), ticks(settings), clock.tickTime()));
			}
		}
	}

	private static boolean isBreak(InteractionKind kind) {
		return kind == InteractionKind.BREAK_DONE || kind == InteractionKind.BREAK_START;
	}

	/** A drawn block that became air (or the fluid it held). */
	private static boolean removed(BlockChange change) {
		BlockState after = change.after();
		boolean gone = after.isAir() || after.getBlock() instanceof LiquidBlock;
		return gone && change.before().getRenderShape() == RenderShape.MODEL;
	}

	private static int ticks(ContactEffectsConfig settings) {
		return Math.clamp(settings.breakShrinkTicks, ContactEffectsConfig.MIN_TICKS, ContactEffectsConfig.MAX_TICKS);
	}
}
