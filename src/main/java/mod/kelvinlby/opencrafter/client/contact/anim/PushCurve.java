package mod.kelvinlby.opencrafter.client.contact.anim;

/**
 * How far a hit entity is pushed over its push, as a share of the full distance: out quickly
 * (ease out) to the full distance, then smoothly back to rest. Zero before and after.
 */
public final class PushCurve {
	/** Share of the push spent going out. */
	static final double OUT_SHARE = 0.3;

	private PushCurve() {
	}

	public static double amount(double progress) {
		if (progress <= 0.0 || progress >= 1.0) {
			return 0.0;
		}
		if (progress < OUT_SHARE) {
			return Easing.out(progress / OUT_SHARE);
		}
		return 1.0 - Easing.smooth((progress - OUT_SHARE) / (1.0 - OUT_SHARE));
	}
}
