package mod.kelvinlby.opencrafter.client.handview.anim;

import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BoxTest {
	private static final Box FULL = box(0, 0, 0, 1, 1, 1);
	private static final Box BOTTOM = box(0, 0, 0, 1, 0.5F, 1);
	private static final Box TOP = box(0, 0.5F, 0, 1, 1, 1);

	@Test
	void fitsABottomSlabOntoTheTopHalf() {
		Vector3f corner = BOTTOM.fitInto(TOP).transformPosition(new Vector3f(1, 0.5F, 1));
		assertEquals(new Vector3f(1, 1, 1), corner);
	}

	@Test
	void aDoubleSlabAddsTheMissingHalf() {
		assertEquals(TOP, Box.added(FULL, BOTTOM));
		assertEquals(BOTTOM, Box.added(FULL, TOP));
	}

	@Test
	void aNewBlockAddsItsWholeShape() {
		assertEquals(FULL, Box.added(FULL, box(0, 0, 0, 0, 0, 0)));
	}

	private static Box box(float x0, float y0, float z0, float x1, float y1, float z1) {
		return new Box(new Vector3f(x0, y0, z0), new Vector3f(x1, y1, z1));
	}
}
