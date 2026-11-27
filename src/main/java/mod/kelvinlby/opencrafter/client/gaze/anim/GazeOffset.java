package mod.kelvinlby.opencrafter.client.gaze.anim;

/**
 * How far the rendered camera is turned away from the player's own rotation, in degrees. It eases
 * toward a desired offset (yaw the shortest way round) and back home to exactly zero, where the
 * camera is vanilla again.
 */
public final class GazeOffset {
	private static final double REST_DEGREES = 1.0E-3;
	private static final double REST_DEGREES_PER_SECOND = 1.0E-2;

	private final CriticalSpring yaw = new CriticalSpring();
	private final CriticalSpring pitch = new CriticalSpring();

	public double yaw() {
		return yaw.value();
	}

	public double pitch() {
		return pitch.value();
	}

	/** Exactly at home and at rest: the camera is the player's own. */
	public boolean isHome() {
		return yaw.value() == 0.0 && pitch.value() == 0.0 && yaw.velocity() == 0.0 && pitch.velocity() == 0.0;
	}

	/** Eases toward the desired offset; the desired yaw may be given in any turn (it is taken the shortest way). */
	public void easeToward(double desiredYaw, double desiredPitch, double settleSeconds, double seconds) {
		yaw.step(Angles.nearestEquivalent(desiredYaw, yaw.value()), settleSeconds, seconds);
		pitch.step(desiredPitch, settleSeconds, seconds);
		keepYawWithinHalfTurn();
	}

	/** Eases back to zero and lands exactly on it once close enough. */
	public void easeHome(double settleSeconds, double seconds) {
		easeToward(0.0, 0.0, settleSeconds, seconds);
		if (isNearHome()) {
			reset();
		}
	}

	public void reset() {
		yaw.set(0.0);
		pitch.set(0.0);
	}

	private boolean isNearHome() {
		return Math.abs(yaw.value()) < REST_DEGREES && Math.abs(pitch.value()) < REST_DEGREES
			&& Math.abs(yaw.velocity()) < REST_DEGREES_PER_SECOND && Math.abs(pitch.velocity()) < REST_DEGREES_PER_SECOND;
	}

	/** A yaw offset past half a turn is the same view the other way round; keeps the numbers small. */
	private void keepYawWithinHalfTurn() {
		double wrapped = Angles.wrapDegrees(yaw.value());
		if (wrapped != yaw.value()) {
			yaw.shift(wrapped - yaw.value());
		}
	}
}
