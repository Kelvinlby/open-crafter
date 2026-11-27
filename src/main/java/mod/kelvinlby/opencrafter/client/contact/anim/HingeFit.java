package mod.kelvinlby.opencrafter.client.contact.anim;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * How a toggled block's new model moves back to its old pose, quad by quad: quads that are the
 * same in both stay still; every moving rigid part turns about its hinge (doors, trapdoors, gate
 * wings, lever handles) or, if no hinge fits, slides (buttons). Pure geometry, block-local.
 */
public final class HingeFit {
	private HingeFit() {
	}

	/** One motion per quad of {@code newQuads}, or null when the change is not a rigid motion of parts. */
	public static @Nullable List<PartMotion> solve(List<QuadPoints> newQuads, List<QuadPoints> oldQuads) {
		List<Integer> movedNew = QuadGroups.moved(newQuads, oldQuads);
		List<Integer> movedOld = QuadGroups.moved(oldQuads, newQuads);
		if (movedNew.isEmpty() || movedOld.isEmpty()) {
			return null;
		}
		List<PartMotion> motions = new ArrayList<>(Collections.nCopies(newQuads.size(), PartMotion.STILL));
		Vector3f[] anchor = anchor(newQuads, movedNew);
		if (swingParts(newQuads, oldQuads, movedNew, movedOld, anchor, motions)) {
			return motions;
		}
		PartMotion.Stretch stretch = StretchFit.find(points(newQuads, movedNew), points(oldQuads, movedOld));
		if (stretch == null) {
			return null;
		}
		movedNew.forEach(i -> motions.set(i, stretch));
		return motions;
	}

	/**
	 * Fits a hinge per rigid part, taking the parts from whichever model shows them apart (an open
	 * gate's wings are separate, a closed gate's touch). False when some part fits no hinge.
	 */
	private static boolean swingParts(List<QuadPoints> newQuads, List<QuadPoints> oldQuads, List<Integer> movedNew, List<Integer> movedOld,
		Vector3f[] anchor, List<PartMotion> motions) {
		List<List<Integer>> newParts = QuadGroups.parts(newQuads, movedNew);
		List<List<Integer>> oldParts = QuadGroups.parts(oldQuads, movedOld);
		if (newParts.size() >= oldParts.size()) {
			return swingNewParts(newQuads, newParts, points(oldQuads, movedOld), anchor, motions);
		}
		return swingOldParts(newQuads, movedNew, oldQuads, oldParts, anchor, motions);
	}

	private static boolean swingNewParts(List<QuadPoints> newQuads, List<List<Integer>> parts, PointSet oldPoints, Vector3f[] anchor,
		List<PartMotion> motions) {
		for (List<Integer> part : parts) {
			PartMotion.Swing swing = SwingFit.find(points(newQuads, part), oldPoints, anchor);
			if (swing == null) {
				return false;
			}
			part.forEach(i -> motions.set(i, swing));
		}
		return true;
	}

	/** Parts fitted in the old model turn onto the new one; the new quads they land on turn back the other way. */
	private static boolean swingOldParts(List<QuadPoints> newQuads, List<Integer> movedNew, List<QuadPoints> oldQuads, List<List<Integer>> parts,
		Vector3f[] anchor, List<PartMotion> motions) {
		PointSet newPoints = points(newQuads, movedNew);
		List<Integer> unassigned = new ArrayList<>(movedNew);
		for (List<Integer> part : parts) {
			PartMotion.Swing swing = SwingFit.find(points(oldQuads, part), newPoints, anchor);
			if (swing == null) {
				return false;
			}
			PointSet landed = PointSet.of(turned(QuadGroups.select(oldQuads, part), swing));
			unassigned.removeIf(i -> {
				boolean onPart = landed.containsAll(corners(newQuads.get(i)));
				if (onPart) {
					motions.set(i, swing.reversed());
				}
				return onPart;
			});
		}
		return unassigned.isEmpty();
	}

	/** Where the hinge should be: at the parts that stay (gate posts, lever base), else on the moving part itself. */
	private static Vector3f[] anchor(List<QuadPoints> newQuads, List<Integer> movedNew) {
		List<Integer> still = new ArrayList<>();
		for (int i = 0; i < newQuads.size(); i++) {
			if (!movedNew.contains(i)) {
				still.add(i);
			}
		}
		return points(newQuads, still.isEmpty() ? movedNew : still).bounds();
	}

	private static List<QuadPoints> turned(List<QuadPoints> quads, PartMotion.Swing swing) {
		Matrix4f matrix = swing.at(1.0);
		return quads.stream().map(quad -> new QuadPoints(quad.corners().stream().<Vector3fc>map(corner -> matrix.transformPosition(new Vector3f(corner))).toList())).toList();
	}

	private static List<Vector3f> corners(QuadPoints quad) {
		return quad.corners().stream().map(Vector3f::new).toList();
	}

	private static PointSet points(List<QuadPoints> quads, List<Integer> indices) {
		return PointSet.of(QuadGroups.select(quads, indices));
	}
}
