package mod.kelvinlby.opencrafter.client.contact.anim;

import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Finds the hinge that turns one set of points onto another: a quarter turn about an axis
 * parallel to x, y or z. Several hinges can fit a symmetric part (a door slab fits two); the one
 * whose axis passes closest to the {@code anchor} box wins, i.e. the part turns where it is
 * attached (the posts of a gate, the base of a lever, the door's own edge).
 */
public final class SwingFit {
	private static final Vector3fc[] AXES = {new Vector3f(1, 0, 0), new Vector3f(0, 1, 0), new Vector3f(0, 0, 1)};
	private static final float QUARTER = (float) (Math.PI / 2);
	private static final float ON_AXIS_TOLERANCE = 1.0e-3F;

	private SwingFit() {
	}

	/** The hinge that maps every point of {@code from} onto a point of {@code to}, or null. */
	public static PartMotion.@Nullable Swing find(PointSet from, PointSet to, Vector3f[] anchor) {
		PartMotion.Swing best = null;
		double bestScore = Double.POSITIVE_INFINITY;
		for (int axis = 0; axis < 3; axis++) {
			for (int sign = -1; sign <= 1; sign += 2) {
				for (PartMotion.Swing swing : candidates(from, to, axis, sign)) {
					double score = distanceToBox(swing.pivot(), axis, anchor);
					if (score < bestScore - 1.0e-6) {
						best = swing;
						bestScore = score;
					}
				}
			}
		}
		return best;
	}

	/** Every quarter turn about {@code axis} that maps {@code from} into {@code to}. */
	private static List<PartMotion.Swing> candidates(PointSet from, PointSet to, int axis, int sign) {
		List<Vector3f> turned = from.points().stream().map(point -> turn(point, axis, sign)).toList();
		List<PartMotion.Swing> found = new ArrayList<>();
		Vector3f first = turned.getFirst();
		for (Vector3fc target : to.points()) {
			Vector3f shift = new Vector3f(target).sub(first);
			if (Math.abs(component(shift, axis)) > ON_AXIS_TOLERANCE) {
				continue;
			}
			List<Vector3f> moved = turned.stream().map(point -> new Vector3f(point).add(shift)).toList();
			if (to.containsAll(moved)) {
				found.add(new PartMotion.Swing(AXES[axis], pivot(shift, axis, sign), sign * QUARTER));
			}
		}
		return found;
	}

	/** A quarter turn about the axis through the origin (right hand, {@code sign} = direction). */
	static Vector3f turn(Vector3fc point, int axis, int sign) {
		float u = component(point, u(axis));
		float v = component(point, v(axis));
		Vector3f out = new Vector3f(point);
		set(out, u(axis), -sign * v);
		set(out, v(axis), sign * u);
		return out;
	}

	/**
	 * The point the turn is about, given that it equals the turn about the origin followed by
	 * {@code shift}: solves {@code (I - R) c = shift} in the plane of the turn.
	 */
	static Vector3f pivot(Vector3fc shift, int axis, int sign) {
		float tu = component(shift, u(axis));
		float tv = component(shift, v(axis));
		// cos = 0, sin = sign: (I - R)^-1 = 1/2 [[1, -sin], [sin, 1]]
		Vector3f pivot = new Vector3f();
		set(pivot, u(axis), (tu - sign * tv) / 2.0F);
		set(pivot, v(axis), (sign * tu + tv) / 2.0F);
		return pivot;
	}

	/** How far the axis through {@code pivot} passes from the box (0 when through it). */
	static double distanceToBox(Vector3fc pivot, int axis, Vector3f[] box) {
		double du = gap(component(pivot, u(axis)), component(box[0], u(axis)), component(box[1], u(axis)));
		double dv = gap(component(pivot, v(axis)), component(box[0], v(axis)), component(box[1], v(axis)));
		return Math.sqrt(du * du + dv * dv);
	}

	private static double gap(float value, float min, float max) {
		return value < min ? min - value : value > max ? value - max : 0.0;
	}

	/** The plane of a turn about x is (y, z), about y (z, x), about z (x, y): right handed. */
	private static int u(int axis) {
		return (axis + 1) % 3;
	}

	private static int v(int axis) {
		return (axis + 2) % 3;
	}

	private static float component(Vector3fc vector, int index) {
		return vector.get(index);
	}

	private static void set(Vector3f vector, int index, float value) {
		vector.setComponent(index, value);
	}
}
