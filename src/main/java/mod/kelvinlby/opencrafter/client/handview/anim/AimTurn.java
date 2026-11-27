package mod.kelvinlby.opencrafter.client.handview.anim;

import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector3fc;

/**
 * Turns a hand's motion toward an action's contact point instead of the crosshair, pivoting at a
 * given point (the eye, for the whole hand). Camera space: x right, y up, -z forward, the eye at the origin.
 */
public final class AimTurn {
	/** The hand turns at most this far, so it stays on screen for contacts far off the view. */
	public static final float MAX_RADIANS = (float) Math.toRadians(30);
	private static final float MIN_LENGTH_SQUARED = 1.0E-8F;
	private static final float MIN_RADIANS = 1.0E-5F;

	private AimTurn() {
	}

	/**
	 * The turn about {@code pivot} that takes the hand's line to the point straight ahead (as far from
	 * the eye as the contact) onto its line to {@code contact}, limited to {@link #MAX_RADIANS} and
	 * scaled by {@code weight} in [0, 1]. A contact on the crosshair, or weight 0, turns nothing.
	 */
	public static Quaternionf toward(Vector3fc pivot, Vector3fc contact, float weight) {
		Vector3f ahead = new Vector3f(0, 0, -contact.length()).sub(pivot);
		Vector3f toContact = new Vector3f(contact).sub(pivot);
		if (weight <= 0 || ahead.lengthSquared() < MIN_LENGTH_SQUARED || toContact.lengthSquared() < MIN_LENGTH_SQUARED) {
			return new Quaternionf();
		}
		Quaternionf full = new Quaternionf().rotationTo(ahead.normalize(), toContact.normalize());
		float angle = full.angle();
		if (angle < MIN_RADIANS) {
			return new Quaternionf();
		}
		float turned = Math.min(angle, MAX_RADIANS) * Math.min(weight, 1);
		return new Quaternionf().slerp(full, turned / angle);
	}
}
