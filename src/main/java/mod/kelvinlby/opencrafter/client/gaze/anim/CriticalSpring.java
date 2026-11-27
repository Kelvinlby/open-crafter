package mod.kelvinlby.opencrafter.client.gaze.anim;

/**
 * One value eased toward a target by a critically damped spring, stepped with the exact solution:
 * frame-rate independent, never overshoots a fixed target, and a moving or new target keeps the
 * value's velocity, so retargeting stays smooth instead of restarting.
 */
public final class CriticalSpring {
	/** The spring's rate per settle time: after one settle time ~9% of a step is left ((1 + 4) e^-4). */
	static final double RATE_PER_SETTLE = 4.0;

	private double value;
	private double velocity;

	public double value() {
		return value;
	}

	/** Units per second. */
	public double velocity() {
		return velocity;
	}

	/** Moves {@code seconds} toward {@code target}; {@code settleSeconds} is roughly the time to arrive. */
	public void step(double target, double settleSeconds, double seconds) {
		if (settleSeconds <= 0.0) {
			set(target);
			return;
		}
		double rate = RATE_PER_SETTLE / settleSeconds;
		double offset = value - target;
		double drive = (velocity + rate * offset) * seconds;
		double decay = Math.exp(-rate * seconds);
		value = target + (offset + drive) * decay;
		velocity = (velocity - rate * drive) * decay;
	}

	/** Jumps to a value, at rest. */
	public void set(double newValue) {
		value = newValue;
		velocity = 0.0;
	}

	/** Moves the value by {@code amount} (e.g. a whole turn) without changing its motion. */
	public void shift(double amount) {
		value += amount;
	}
}
