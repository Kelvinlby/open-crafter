package mod.kelvinlby.opencrafter.client.handview.anim;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector3fc;

/** An axis-aligned box in block units. */
public record Box(Vector3fc min, Vector3fc max) {
	private static final float EPSILON = 1e-4F;

	public Vector3f size() {
		return new Vector3f(max).sub(min);
	}

	public Vector3f centre() {
		return new Vector3f(min).add(max).mul(0.5F);
	}

	public boolean isEmpty() {
		Vector3f size = size();
		return size.x <= EPSILON && size.y <= EPSILON && size.z <= EPSILON;
	}

	/** Maps this box onto {@code target}, stretching each axis on its own. */
	public Matrix4f fitInto(Box target) {
		Vector3f size = size();
		Vector3f targetSize = target.size();
		Vector3f scale = new Vector3f(ratio(targetSize.x, size.x), ratio(targetSize.y, size.y), ratio(targetSize.z, size.z));
		return new Matrix4f().translate(target.min).scale(scale).translate(new Vector3f(min).negate());
	}

	/**
	 * What {@code after} added to {@code before} when one box grew out of the other along one axis
	 * (a slab made double); otherwise all of {@code after}.
	 */
	public static Box added(Box after, Box before) {
		if (before.isEmpty()) {
			return after;
		}
		for (int axis = 0; axis < 3; axis++) {
			if (sameOnOtherAxes(after, before, axis)) {
				Box grown = grownAlong(after, before, axis);
				if (grown != null) {
					return grown;
				}
			}
		}
		return after;
	}

	private static boolean sameOnOtherAxes(Box a, Box b, int axis) {
		for (int other = 0; other < 3; other++) {
			if (other != axis && (!near(a.min.get(other), b.min.get(other)) || !near(a.max.get(other), b.max.get(other)))) {
				return false;
			}
		}
		return true;
	}

	private static Box grownAlong(Box after, Box before, int axis) {
		if (near(after.min.get(axis), before.min.get(axis)) && after.max.get(axis) > before.max.get(axis) + EPSILON) {
			return new Box(withAxis(after.min, axis, before.max.get(axis)), after.max);
		}
		if (near(after.max.get(axis), before.max.get(axis)) && after.min.get(axis) < before.min.get(axis) - EPSILON) {
			return new Box(after.min, withAxis(after.max, axis, before.min.get(axis)));
		}
		return null;
	}

	private static Vector3f withAxis(Vector3fc v, int axis, float value) {
		return new Vector3f(v).setComponent(axis, value);
	}

	private static boolean near(float a, float b) {
		return Math.abs(a - b) <= EPSILON;
	}

	private static float ratio(float target, float source) {
		return source <= EPSILON ? 1 : target / source;
	}
}
