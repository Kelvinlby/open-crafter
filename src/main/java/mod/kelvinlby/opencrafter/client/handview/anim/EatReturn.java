package mod.kelvinlby.opencrafter.client.handview.anim;

/**
 * The eating pose's way back to the hand after a use ends: it starts from where the pose was
 * and eases down to rest instead of vanilla's jump.
 */
public final class EatReturn {
	public static final double RETURN_TICKS = RightClickPace.ticks(5);

	private double start = Double.NEGATIVE_INFINITY;
	private double level;
	private double bob;

	/** The use ended at {@code now} with the pose at {@code level} (0 rest, 1 middle) and this bob height. */
	public void release(double now, double level, double bob) {
		this.start = now;
		this.level = level;
		this.bob = bob;
	}

	/** How far in the pose still is: 0 once back at rest. */
	public double level(double now) {
		return level * remaining(now);
	}

	/** The bob height still left. */
	public double bob(double now) {
		return bob * remaining(now);
	}

	private double remaining(double now) {
		return 1 - Easing.smooth((now - start) / RETURN_TICKS);
	}
}
