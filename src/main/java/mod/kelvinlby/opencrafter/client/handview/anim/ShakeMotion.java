package mod.kelvinlby.opencrafter.client.handview.anim;

/**
 * Right click on an entity or block: the hand shakes along a fixed axis while the button is
 * held and settles back to rest once released. Output is an offset along the axis and a tilt.
 */
public final class ShakeMotion {
	private static final double RISE_TICKS = RightClickPace.ticks(2);
	private static final double FALL_TICKS = RightClickPace.ticks(3);
	private static final double PERIOD_TICKS = RightClickPace.ticks(4);
	private static final double AMPLITUDE = 0.07;
	private static final double TILT_DEGREES = 6;

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

	/** Signed distance along the shake axis. */
	public float offset(double now) {
		return (float) (AMPLITUDE * envelope.value(now) * wave(now));
	}

	/** Tilt in degrees that follows the offset. */
	public float tilt(double now) {
		return (float) (TILT_DEGREES * envelope.value(now) * wave(now));
	}

	private double wave(double now) {
		return Math.sin(2 * Math.PI * envelope.cycleTime(now) / PERIOD_TICKS);
	}
}
