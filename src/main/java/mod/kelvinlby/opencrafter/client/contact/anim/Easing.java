package mod.kelvinlby.opencrafter.client.contact.anim;

/** Easing curves on {@code [0, 1]}; inputs outside are clamped. */
public final class Easing {
	private Easing() {
	}

	public static double clamp01(double t) {
		return Math.max(0.0, Math.min(1.0, t));
	}

	/** Smoothstep: starts and ends at rest. */
	public static double smooth(double t) {
		double x = clamp01(t);
		return x * x * (3.0 - 2.0 * x);
	}

	/** Fast start, gentle stop. */
	public static double out(double t) {
		double x = 1.0 - clamp01(t);
		return 1.0 - x * x;
	}
}
