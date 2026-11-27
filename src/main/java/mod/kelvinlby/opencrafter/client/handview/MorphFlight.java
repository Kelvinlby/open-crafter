package mod.kelvinlby.opencrafter.client.handview;

import mod.kelvinlby.opencrafter.client.handview.anim.Travel;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.InteractionHand;
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
		/**
		 * At the block, drawn on it while the block is redrawn underneath; until a frame recorded for
		 * optical flow saw it there, so the flow hands its pixels over to the block.
		 */
		ARRIVED,
		DONE
	}

	final BlockMorph.Target target;
	/** The model it is drawn with (the stack as it was before the click). */
	final ItemStackRenderState model;
	/** The hand it left. */
	final InteractionHand hand;
	/** Where it left the hand, in camera space, before the item's display transform. */
	final Matrix4f from;
	/** Longest stay on the block waiting for a recorded frame (ticks); covers capture rates down to 4 Hz. */
	private static final double MAX_SETTLE_TICKS = 5;

	private final Travel travel = new Travel();
	private Phase phase = Phase.TRAVEL;
	private double arrivedAt;
	private boolean settledRecorded;

	MorphFlight(BlockMorph.Target target, ItemStackRenderState model, InteractionHand hand, Matrix4fc from, double now, double durationTicks) {
		this.target = target;
		this.model = model;
		this.hand = hand;
		this.from = new Matrix4f(from);
		travel.start(now, durationTicks);
	}

	/** Once per tick. {@code waitForRecord}: optical flow is captured, so stay until a recorded frame saw it settled. */
	void tick(double now, boolean waitForRecord) {
		if (phase == Phase.ARRIVED && settledLongEnough(now, waitForRecord)) {
			phase = Phase.DONE;
		} else if (phase == Phase.TRAVEL && travel.arrived(now)) {
			phase = Phase.ARRIVED;
			arrivedAt = now;
		}
	}

	/** On its block with the block drawn again underneath (from the tick after arriving). */
	boolean settled(double now) {
		return phase == Phase.ARRIVED && now - arrivedAt >= 1;
	}

	/** A frame recorded for optical flow drew it {@link #settled}. */
	void noteSettledRecorded() {
		settledRecorded = true;
	}

	private boolean settledLongEnough(double now, boolean waitForRecord) {
		return now - arrivedAt >= 1 && (!waitForRecord || settledRecorded || now - arrivedAt >= MAX_SETTLE_TICKS);
	}

	/** Stops at once (it left the screen): the block shows up. */
	void end() {
		phase = Phase.DONE;
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
