package mod.kelvinlby.opencrafter.core.config.handview;

/** How hand swings look in first person. */
public final class SwingConfig {
	/** Which hand the local player's swings show in. */
	public enum SwingHand {
		VANILLA,
		MAIN_HAND,
		OFF_HAND
	}

	/** The swing a left click (attack, mining) plays. */
	public enum LeftClickAnimation {
		VANILLA,
		/** A short jab to 3-5 % of the swing that trembles there while held. */
		STUB
	}

	/** The swing a right click (use, place, interact, eat) plays. */
	public enum RightClickAnimation {
		VANILLA,
		/** Continuous motions: eat from where the hand is, fly to the placed block, shake while interacting. */
		SMOOTH
	}

	public static final int MIN_SWING_TICKS = 0;
	public static final int MAX_SWING_TICKS = 20;
	public static final int VANILLA_SWING_TICKS = 6;

	/** 1.8 style: the main hand stays raised after an attack instead of following the cooldown. */
	public boolean oldAnimations = false;
	public SwingHand swingHand = SwingHand.VANILLA;
	/** Ticks a first-person swing lasts. */
	public int swingTicks = VANILLA_SWING_TICKS;
	public LeftClickAnimation leftClick = LeftClickAnimation.VANILLA;
	public RightClickAnimation rightClick = RightClickAnimation.VANILLA;
}
