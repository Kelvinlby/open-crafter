package mod.kelvinlby.opencrafter.client.inventoryview.anim;

/**
 * A reversible linear progress on [0, 1]: it moves toward its target at a constant speed, and a
 * new target turns it around from wherever it is, so reversing mid-way never jumps.
 */
public final class Slide {
	private double from;
	private double target;
	private double startTime;
	private double duration = 1;

	/** Moves toward {@code target}, taking {@code duration} for the full range. */
	public void moveTo(double now, double target, double duration) {
		this.from = progress(now);
		this.target = target;
		this.startTime = now;
		this.duration = Math.max(1e-6, duration);
	}

	/** Stands still at {@code value}. */
	public void jumpTo(double value) {
		from = value;
		target = value;
	}

	public double progress(double now) {
		double step = Math.max(0, now - startTime) / duration;
		return target >= from ? Math.min(target, from + step) : Math.max(target, from - step);
	}

	public double target() {
		return target;
	}

	public boolean settled(double now) {
		return progress(now) == target;
	}
}
