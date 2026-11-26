package mod.kelvinlby.opencrafter.client.handview.anim;

/** Easing curves on [0, 1]; inputs outside are clamped. */
public final class Easing {
	private static final int INVERSE_STEPS = 24;

	private Easing() {
	}

	public static double clamp01(double t) {
		return Math.max(0, Math.min(1, t));
	}

	/** Zero speed at both ends. */
	public static double smooth(double t) {
		double x = clamp01(t);
		return x * x * (3 - 2 * x);
	}

	/** Fast start, gentle stop. */
	public static double out(double t) {
		double x = 1 - clamp01(t);
		return 1 - x * x * x;
	}

	/** The t in [0, 1] with {@code smooth(t) == value}. */
	public static double inverseSmooth(double value) {
		double target = clamp01(value);
		double low = 0;
		double high = 1;
		for (int i = 0; i < INVERSE_STEPS; i++) {
			double mid = (low + high) / 2;
			if (smooth(mid) < target) {
				low = mid;
			} else {
				high = mid;
			}
		}
		return (low + high) / 2;
	}
}
