package mod.kelvinlby.opencrafter.client.handview.anim;

/**
 * Left click "stub": instead of a full swing, the swing progress jabs from 0 to about 4 %,
 * trembles between 3 % and 5 % while the button is held, and eases back to 0 when released.
 */
public final class StubSwing {
	public static final double CENTRE = 0.04;
	public static final double TREMBLE = 0.01;
	private static final double RISE_TICKS = 1.5;
	private static final double FALL_TICKS = 2.5;
	private static final double TREMBLE_PERIOD_TICKS = 4;

	private final Envelope envelope = new Envelope(RISE_TICKS, FALL_TICKS);

	public void engage(double now) {
		envelope.engage(now);
	}

	public void update(double now, boolean held) {
		envelope.update(now, held);
	}

	public void reset() {
		envelope.reset();
	}

	public boolean active() {
		return envelope.active();
	}

	/** The swing progress to show, in [0, CENTRE + TREMBLE]. */
	public float progress(double now) {
		double level = envelope.value(now);
		double tremble = Math.sin(2 * Math.PI * envelope.cycleTime(now) / TREMBLE_PERIOD_TICKS);
		return (float) (level * (CENTRE + TREMBLE * level * tremble));
	}
}
