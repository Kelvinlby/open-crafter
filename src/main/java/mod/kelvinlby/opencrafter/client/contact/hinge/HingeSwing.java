package mod.kelvinlby.opencrafter.client.contact.hinge;

import mod.kelvinlby.opencrafter.client.contact.EffectClock;
import mod.kelvinlby.opencrafter.client.contact.anim.HingeFit;
import mod.kelvinlby.opencrafter.client.contact.anim.PartMotion;
import mod.kelvinlby.opencrafter.client.contact.render.ModelQuads;
import mod.kelvinlby.opencrafter.client.contact.render.TempBlocks;
import mod.kelvinlby.opencrafter.client.interaction.event.BlockChange;
import mod.kelvinlby.opencrafter.client.interaction.event.InteractionEvent;
import mod.kelvinlby.opencrafter.client.interaction.event.InteractionKind;
import mod.kelvinlby.opencrafter.client.interaction.event.InteractionListener;
import mod.kelvinlby.opencrafter.client.interaction.event.Outcome;
import mod.kelvinlby.opencrafter.core.config.ConfigStore;
import mod.kelvinlby.opencrafter.core.config.ContactEffectsConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/**
 * A confirmed use on a door, trapdoor, fence gate, lever or button that only switched it: its
 * moving part swings (or slides) from the old state to the new one instead of snapping. Render only.
 */
public final class HingeSwing implements InteractionListener {
	private final ConfigStore config;
	private final TempBlocks blocks;
	private final HiddenBlocks hidden;
	private final EffectClock clock;

	public HingeSwing(ConfigStore config, TempBlocks blocks, HiddenBlocks hidden, EffectClock clock) {
		this.config = config;
		this.blocks = blocks;
		this.hidden = hidden;
		this.clock = clock;
	}

	@Override
	public void onOutcome(InteractionEvent event, Outcome outcome) {
		ContactEffectsConfig settings = config.get().contactEffects;
		ClientLevel level = Minecraft.getInstance().level;
		if (!settings.hingeSwing || !outcome.effect() || event.kind() != InteractionKind.USE_ON_BLOCK || level == null) {
			return;
		}
		for (BlockChange change : withOtherHalves(level, outcome.blockChanges())) {
			if (HingeBlocks.toggled(change.before(), change.after()) && level.getBlockState(change.pos()).equals(change.after())) {
				swing(level, change, ticks(settings));
			}
		}
	}

	/** Clicking one half of a door opens both; only the clicked one is in the outcome. */
	private static List<BlockChange> withOtherHalves(ClientLevel level, List<BlockChange> changes) {
		List<BlockChange> all = new ArrayList<>(changes);
		for (BlockChange change : changes) {
			BlockPos other = HingeBlocks.otherHalf(change.pos(), change.after());
			if (other != null && changes.stream().noneMatch(known -> known.pos().equals(other))) {
				BlockState now = level.getBlockState(other);
				if (now.getBlock() == change.after().getBlock()) {
					all.add(new BlockChange(other, HingeBlocks.withOpenOf(now, change.before()), now));
				}
			}
		}
		return all;
	}

	private void swing(ClientLevel level, BlockChange change, int ticks) {
		if (hasBlockEntity(level, change.pos())) {
			return;
		}
		List<BakedQuad> quads = ModelQuads.of(change.after(), change.pos());
		List<PartMotion> motions = HingeFit.solve(ModelQuads.points(quads), ModelQuads.points(ModelQuads.of(change.before(), change.pos())));
		if (motions == null) {
			return;
		}
		SwingingBlock block = new SwingingBlock(change.pos(), change.after(), quads, motions, hidden, ticks, clock.tickTime());
		blocks.add(block);
		block.start(level);
	}

	/** Blocks drawn by a block entity renderer animate themselves (chests, barrels): left alone. */
	private static boolean hasBlockEntity(ClientLevel level, BlockPos pos) {
		BlockEntity blockEntity = level.getBlockEntity(pos);
		return blockEntity != null;
	}

	private static int ticks(ContactEffectsConfig settings) {
		return Math.clamp(settings.hingeSwingTicks, ContactEffectsConfig.MIN_TICKS, ContactEffectsConfig.MAX_TICKS);
	}
}
