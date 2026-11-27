package mod.kelvinlby.opencrafter.core.config;

/**
 * Contact effects: an action's consequence shown at the point where it happened, continuously
 * instead of in one frame, and only when the world really had that consequence. Purely visual.
 */
public final class ContactEffectsConfig {
	public static final int MIN_TICKS = 1;
	public static final int MAX_TICKS = 40;
	public static final double MAX_PUSH_BLOCKS = 0.3;

	/** A break another mod drives (game mode or raw packets) cracks the block like a keyboard break. */
	public boolean crackOverlay = true;
	/** How long the cracks stay at their last stage once the block should have broken, waiting for the break to finish. */
	public int crackLingerTicks = 20;

	/** A broken block shrinks to its centre instead of vanishing at once. */
	public boolean breakShrink = true;
	public int breakShrinkTicks = 4;

	/** Doors, trapdoors, fence gates, levers and buttons swing or slide to their new state instead of snapping. */
	public boolean hingeSwing = true;
	public int hingeSwingTicks = 4;

	/** A hurt entity is pushed a little away from where it was hit, and comes back. Render only. */
	public boolean hitPush = true;
	public int hitPushTicks = 4;
	/** How far the push goes, in blocks. */
	public double hitPushBlocks = 0.06;
}
