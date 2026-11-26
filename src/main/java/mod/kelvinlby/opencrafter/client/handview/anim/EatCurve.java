package mod.kelvinlby.opencrafter.client.handview.anim;

/**
 * How far the eating/drinking pose has moved the item into the middle (0 at rest, 1 there).
 * Vanilla jumps most of the way in the first tick; the smooth curve eases in from rest.
 */
public final class EatCurve {
	public static final double VANILLA_EXPONENT = 27;
	/** Vanilla's bob starts once this much of the use is left. */
	public static final double BOB_START = 0.8;
	private static final double SMOOTH_IN_TICKS = 6;
	private static final double BOB_FADE_TICKS = 3;

	private EatCurve() {
	}

	/** {@code remaining}: use ticks left, including the partial tick; {@code duration}: the item's use duration. */
	public static float blend(double remaining, int duration, boolean smooth) {
		double left = remaining / duration;
		return (float) (smooth ? smoothBlend(left, duration) : 1 - Math.pow(left, VANILLA_EXPONENT));
	}

	/** {@code left}: the fraction of the use still to go. */
	public static double smoothBlend(double left, int duration) {
		return Easing.smooth(duration * (1 - left) / SMOOTH_IN_TICKS);
	}

	/** The bob's strength: fades in from its start instead of popping in. */
	public static double smoothBobFade(double left, int duration) {
		return Easing.smooth((BOB_START - left) * duration / BOB_FADE_TICKS);
	}
}
