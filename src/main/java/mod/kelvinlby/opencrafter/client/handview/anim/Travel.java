package mod.kelvinlby.opencrafter.client.handview.anim;

/** A one-way motion: {@link #weight} eases from 0 to 1 over its duration and then stays there. */
public final class Travel {
	private double start;
	private double durationTicks = 1;
	private boolean started;

	public void start(double now, double durationTicks) {
		this.start = now;
		this.durationTicks = Math.max(1, durationTicks);
		this.started = true;
	}

	public void reset() {
		started = false;
	}

	public float weight(double now) {
		return started ? (float) Easing.smooth((now - start) / durationTicks) : 0;
	}

	/** Started and at its end. */
	public boolean arrived(double now) {
		return started && now - start >= durationTicks;
	}
}
