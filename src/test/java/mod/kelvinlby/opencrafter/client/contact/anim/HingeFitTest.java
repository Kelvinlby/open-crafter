package mod.kelvinlby.opencrafter.client.contact.anim;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HingeFitTest {
	private static final float EPS = 1.0e-4F;

	@Test
	void aDoorTurnsAboutItsHingeEdge() {
		List<QuadPoints> closed = box(0, 0, 0, 3, 16, 16);
		List<QuadPoints> open = box(0, 0, 13, 16, 16, 16);
		List<PartMotion> motions = HingeFit.solve(open, closed);
		assertNotNull(motions);
		PartMotion.Swing swing = assertInstanceOf(PartMotion.Swing.class, motions.getFirst());
		assertEquals(new Vector3f(0, 1, 0), swing.axis());
		assertEquals(1.5F / 16, swing.pivot().x(), EPS);
		assertEquals(14.5F / 16, swing.pivot().z(), EPS);
		assertMapsOnto(open, motions, closed);
	}

	@Test
	void halfWayADoorIsDiagonal() {
		List<QuadPoints> closed = box(0, 0, 0, 3, 16, 16);
		List<QuadPoints> open = box(0, 0, 13, 16, 16, 16);
		List<PartMotion> motions = HingeFit.solve(open, closed);
		assertNotNull(motions);
		// The far edge of the open door (x = 16) swings around the hinge, staying at its distance from it
		Vector3f far = motions.getFirst().at(0.5).transformPosition(new Vector3f(1, 0.5F, 1));
		Vector3f pivot = new Vector3f(1.5F / 16, 0.5F, 14.5F / 16);
		assertEquals(new Vector3f(1, 0.5F, 1).distance(pivot), far.distance(pivot), EPS);
		assertTrue(far.x < 1 - EPS && far.z < 1 - EPS);
	}

	@Test
	void aLeverHandleTurnsAboutItsBaseWhichStays() {
		List<QuadPoints> base = box(5, 0, 4, 11, 3, 12);
		List<QuadPoints> off = join(base, tilted(box(7, 1, 7, 9, 11, 9), 45));
		List<QuadPoints> on = join(base, tilted(box(7, 1, 7, 9, 11, 9), -45));
		List<PartMotion> motions = HingeFit.solve(on, off);
		assertNotNull(motions);
		for (int i = 0; i < base.size(); i++) {
			assertSame(PartMotion.STILL, motions.get(i));
		}
		PartMotion.Swing swing = assertInstanceOf(PartMotion.Swing.class, motions.get(base.size()));
		assertEquals(1.0F / 16, swing.pivot().y(), EPS);
		assertEquals(8.0F / 16, swing.pivot().z(), EPS);
		assertMapsOnto(on, motions, off);
	}

	@Test
	void gateWingsTurnAboutTheirPosts() {
		List<QuadPoints> posts = join(box(0, 5, 7, 2, 16, 9), box(14, 5, 7, 16, 16, 9));
		List<QuadPoints> closed = join(posts, join(box(2, 6, 7, 8, 15, 9), box(8, 6, 7, 14, 15, 9)));
		List<QuadPoints> open = join(posts, join(box(0, 6, 9, 2, 15, 15), box(14, 6, 9, 16, 15, 15)));
		List<PartMotion> opening = HingeFit.solve(open, closed);
		assertNotNull(opening);
		assertMapsOnto(open, opening, closed);
		PartMotion.Swing left = assertInstanceOf(PartMotion.Swing.class, opening.get(posts.size()));
		assertEquals(1.0F / 16, left.pivot().x(), EPS);
		assertEquals(8.0F / 16, left.pivot().z(), EPS);

		List<PartMotion> closing = HingeFit.solve(closed, open);
		assertNotNull(closing);
		assertMapsOnto(closed, closing, open);
	}

	@Test
	void aButtonSlidesIn() {
		List<QuadPoints> up = box(5, 0, 6, 11, 2, 10);
		List<QuadPoints> pressed = box(5, 0, 6, 11, 1, 10);
		List<PartMotion> motions = HingeFit.solve(pressed, up);
		assertNotNull(motions);
		assertSame(PartMotion.STILL, motions.getFirst(), "the face on the wall stays");
		assertInstanceOf(PartMotion.Stretch.class, motions.get(1));
		assertMapsOnto(pressed, motions, up);
	}

	@Test
	void unrelatedShapesDoNotAnimate() {
		assertNull(HingeFit.solve(join(box(0, 0, 0, 4, 4, 4), box(12, 0, 0, 16, 4, 4)), box(4, 0, 4, 12, 3, 9)));
		assertNull(HingeFit.solve(box(0, 0, 0, 16, 16, 16), box(0, 0, 0, 16, 16, 16)));
	}

	/** At {@code remaining = 1} every quad of the new model sits on a corner of the old one; at 0 it is unmoved. */
	private static void assertMapsOnto(List<QuadPoints> newQuads, List<PartMotion> motions, List<QuadPoints> oldQuads) {
		PointSet old = PointSet.of(oldQuads);
		for (int i = 0; i < newQuads.size(); i++) {
			Matrix4f start = motions.get(i).at(1.0);
			Matrix4f end = motions.get(i).at(0.0);
			for (Vector3fc corner : newQuads.get(i).corners()) {
				assertTrue(old.contains(start.transformPosition(new Vector3f(corner))), "quad " + i + " corner " + corner);
				assertTrue(end.transformPosition(new Vector3f(corner)).distance(corner) < EPS);
			}
		}
	}

	private static List<QuadPoints> join(List<QuadPoints> a, List<QuadPoints> b) {
		List<QuadPoints> all = new ArrayList<>(a);
		all.addAll(b);
		return all;
	}

	private static List<QuadPoints> tilted(List<QuadPoints> quads, float degrees) {
		Matrix4f tilt = new Matrix4f().translate(0.5F, 1.0F / 16, 0.5F).rotateX((float) Math.toRadians(degrees)).translate(-0.5F, -1.0F / 16, -0.5F);
		return quads.stream().map(quad -> new QuadPoints(quad.corners().stream()
			.<Vector3fc>map(corner -> tilt.transformPosition(new Vector3f(corner))).toList())).toList();
	}

	/** The six faces of a box given in sixteenths. */
	static List<QuadPoints> box(float x0, float y0, float z0, float x1, float y1, float z1) {
		Vector3f a = new Vector3f(x0, y0, z0).div(16);
		Vector3f b = new Vector3f(x1, y1, z1).div(16);
		return List.of(
			quad(v(a.x, a.y, a.z), v(b.x, a.y, a.z), v(b.x, a.y, b.z), v(a.x, a.y, b.z)),
			quad(v(a.x, b.y, a.z), v(b.x, b.y, a.z), v(b.x, b.y, b.z), v(a.x, b.y, b.z)),
			quad(v(a.x, a.y, a.z), v(b.x, a.y, a.z), v(b.x, b.y, a.z), v(a.x, b.y, a.z)),
			quad(v(a.x, a.y, b.z), v(b.x, a.y, b.z), v(b.x, b.y, b.z), v(a.x, b.y, b.z)),
			quad(v(a.x, a.y, a.z), v(a.x, b.y, a.z), v(a.x, b.y, b.z), v(a.x, a.y, b.z)),
			quad(v(b.x, a.y, a.z), v(b.x, b.y, a.z), v(b.x, b.y, b.z), v(b.x, a.y, b.z))
		);
	}

	private static QuadPoints quad(Vector3fc... corners) {
		return new QuadPoints(List.of(corners));
	}

	private static Vector3fc v(float x, float y, float z) {
		return new Vector3f(x, y, z);
	}
}
