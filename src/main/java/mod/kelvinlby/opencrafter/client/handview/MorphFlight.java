package mod.kelvinlby.opencrafter.client.handview;

import mod.kelvinlby.opencrafter.client.handview.anim.Travel;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;

/**
 * One placed block's item flying from the hand into the block. Flights are independent, so blocks
 * placed in quick succession each get their own and several can be in the air at once.
 */
final class MorphFlight {
	private enum Phase {
		/** On its way; the block stays held back. */
		TRAVEL,
		/** At the block, drawn one more tick while the block is redrawn underneath. */
		ARRIVED,
		DONE
	}

	final BlockMorph.Target target;
	/** The model it is drawn with (the stack as it was before the click). */
	final ItemStackRenderState model;
	/** Where it left the hand, in camera space, before the item's display transform. */
	final Matrix4f from;
	private final Travel travel = new Travel();
	private Phase phase = Phase.TRAVEL;

	MorphFlight(BlockMorph.Target target, ItemStackRenderState model, Matrix4fc from, double now, double durationTicks) {
		this.target = target;
		this.model = model;
		this.from = new Matrix4f(from);
		travel.start(now, durationTicks);
	}

	/** Once per tick. */
	void tick(double now) {
		if (phase == Phase.ARRIVED) {
			phase = Phase.DONE;
		} else if (phase == Phase.TRAVEL && travel.arrived(now)) {
			phase = Phase.ARRIVED;
		}
	}

	boolean travelling() {
		return phase == Phase.TRAVEL;
	}

	boolean done() {
		return phase == Phase.DONE;
	}

	float weight(double now) {
		return travel.weight(now);
	}
}
