package mod.kelvinlby.opencrafter.client.handview.anim;

/** How fast the smooth right click animations play (eating, shaking, placing); one knob for all of them. */
public final class RightClickPace {
	/** Speed relative to the base timings. */
	public static final double SPEED = 1.25;

	private RightClickPace() {
	}

	/** A base duration in ticks, played at this pace. */
	public static double ticks(double baseTicks) {
		return baseTicks / SPEED;
	}
}
