package mod.kelvinlby.opencrafter.client.contact.render;

import mod.kelvinlby.opencrafter.client.vision.VisionHooks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * The temporary block models currently drawn by contact effects. Each is submitted as a world
 * object of its own for the vision sensor, so its motion is in the optical flow. Client/render thread.
 */
public final class TempBlocks {
	/** Farther away than this (squared blocks) nothing is drawn: vanilla culls block cracks at the same distance. */
	private static final double MAX_DISTANCE_SQ = 1024.0;

	private final List<TempBlock> active = new ArrayList<>();

	public void add(TempBlock block) {
		active.removeIf(other -> replaced(other, block.pos()));
		active.add(block);
	}

	public boolean isEmpty() {
		return active.isEmpty();
	}

	/** Once per tick: drops the blocks whose effect is over. */
	public void tick(double now) {
		active.removeIf(block -> !block.tick(now));
	}

	/** Stops everything at once (effects switched off, world left). */
	public void clear() {
		active.forEach(TempBlock::cancel);
		active.clear();
	}

	/** Inside the level's feature submission. */
	public void submit(SubmitNodeCollector collector, Vec3 camera, double now) {
		ClientLevel level = Minecraft.getInstance().level;
		if (level == null || active.isEmpty()) {
			return;
		}
		for (TempBlock block : active) {
			if (block.pos().distToCenterSqr(camera) <= MAX_DISTANCE_SQ) {
				submitTracked(block, collector, camera, level, now);
			}
		}
	}

	private static void submitTracked(TempBlock block, SubmitNodeCollector collector, Vec3 camera, ClientLevel level, double now) {
		boolean settled = block.settled(now);
		boolean recorded = VisionHooks.beginWorldObject(block, settled);
		try {
			TempBlockRenderer.submit(block, block.pose(now), collector, camera, level);
			if (recorded && settled) {
				block.noteSettledRecorded();
			}
		} finally {
			VisionHooks.endOwner();
		}
	}

	private static boolean replaced(TempBlock old, BlockPos pos) {
		if (old.pos().equals(pos)) {
			old.cancel();
			return true;
		}
		return false;
	}
}
