package mod.kelvinlby.opencrafter.client.contact.anim;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * For parts that slide rather than turn (a button pressed in): the per-axis scale and shift that
 * maps the bounding box of {@code from} onto that of {@code to}, if it maps every point onto one.
 */
public final class StretchFit {
	private static final float FLAT = 1.0e-4F;

	private StretchFit() {
	}

	public static PartMotion.@Nullable Stretch find(PointSet from, PointSet to) {
		Matrix4f map = boxMap(from.bounds(), to.bounds());
		List<Vector3f> mapped = from.points().stream().map(point -> map.transformPosition(new Vector3f(point))).toList();
		return to.containsAll(mapped) ? new PartMotion.Stretch(map) : null;
	}

	private static Matrix4f boxMap(Vector3f[] from, Vector3f[] to) {
		Vector3f fromSize = new Vector3f(from[1]).sub(from[0]);
		Vector3f toSize = new Vector3f(to[1]).sub(to[0]);
		Vector3f scale = new Vector3f(ratio(toSize.x, fromSize.x), ratio(toSize.y, fromSize.y), ratio(toSize.z, fromSize.z));
		return new Matrix4f().translate(to[0]).scale(scale).translate(new Vector3f(from[0]).negate());
	}

	private static float ratio(float to, float from) {
		return from < FLAT ? 1.0F : to / from;
	}
}
