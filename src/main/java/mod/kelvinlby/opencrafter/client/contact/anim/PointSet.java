package mod.kelvinlby.opencrafter.client.contact.anim;

import org.joml.Vector3f;
import org.joml.Vector3fc;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** The distinct corner points of some quads, with a tolerant membership test. */
public final class PointSet {
	/** Model coordinates are sixteenths; 1/4096 of a block tells every real corner apart. */
	private static final float GRID = 4096.0F;
	/** Matches survive float error of rotated corners. */
	private static final float TOLERANCE = 1.0F / 1024.0F;

	private final List<Vector3fc> points;

	private PointSet(List<Vector3fc> points) {
		this.points = points;
	}

	public static PointSet of(Collection<QuadPoints> quads) {
		Map<Long, Vector3fc> distinct = new LinkedHashMap<>();
		for (QuadPoints quad : quads) {
			for (Vector3fc corner : quad.corners()) {
				distinct.putIfAbsent(key(corner), corner);
			}
		}
		return new PointSet(new ArrayList<>(distinct.values()));
	}

	public List<Vector3fc> points() {
		return points;
	}

	public boolean contains(Vector3fc point) {
		for (Vector3fc candidate : points) {
			if (candidate.distanceSquared(point) <= TOLERANCE * TOLERANCE) {
				return true;
			}
		}
		return false;
	}

	public boolean containsAll(List<Vector3f> candidates) {
		for (Vector3f candidate : candidates) {
			if (!contains(candidate)) {
				return false;
			}
		}
		return true;
	}

	/** The bounding box's minimum and maximum corner. */
	public Vector3f[] bounds() {
		Vector3f min = new Vector3f(Float.POSITIVE_INFINITY);
		Vector3f max = new Vector3f(Float.NEGATIVE_INFINITY);
		points.forEach(point -> {
			min.min(point);
			max.max(point);
		});
		return new Vector3f[] {min, max};
	}

	/** A grid cell id for exact comparisons (signatures, shared corners). */
	static long key(Vector3fc point) {
		long x = Math.round(point.x() * GRID) & 0x1FFFFF;
		long y = Math.round(point.y() * GRID) & 0x1FFFFF;
		long z = Math.round(point.z() * GRID) & 0x1FFFFF;
		return x << 42 | y << 21 | z;
	}
}
