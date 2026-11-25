package mod.kelvinlby.opencrafter.client.vision.gui;

import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GuiMotionTest {
	private static final Object PIPELINE = new Object();
	private static final float[] SCREEN = {0, 0, 100, 100};

	/** A 16x16 quad at (x, y); {@code sprite} stands in for the texture region (item, glyph...). */
	private static GuiElement quad(int order, float x, float y, float sprite) {
		GuiElement element = new GuiElement(order, PIPELINE, PrimitiveTopology.QUADS, null, null, SCREEN);
		float[][] corners = {{0, 0}, {0, 16}, {16, 16}, {16, 0}};
		for (float[] corner : corners) {
			element.addVertex(x + corner[0], y + corner[1]);
			element.setUv(sprite + corner[0] / 16, corner[1] / 16);
		}
		return element;
	}

	private static GuiFrame frame(GuiElement... elements) {
		return new GuiFrame(new ArrayList<>(List.of(elements)), 100, 100);
	}

	@Test
	void stationaryElementsAreNotMotion() {
		GuiMotion motion = GuiMotion.between(frame(quad(0, 10, 10, 1)), frame(quad(0, 10, 10, 1)));
		assertTrue(motion.isEmpty());
	}

	@Test
	void pickingOneOfThreeIdenticalItemsMatchesTheOthersToThemselves() {
		GuiElement a = quad(0, 0, 0, 1);
		GuiElement b = quad(1, 20, 0, 1);
		GuiElement c = quad(2, 40, 0, 1);
		GuiElement carried = quad(3, 22, 30, 1);
		GuiMotion motion = GuiMotion.between(frame(a, b, c), frame(quad(0, 0, 0, 1), quad(1, 40, 0, 1), carried));
		assertEquals(1, motion.moved().size());
		assertSame(b, motion.moved().getFirst().prev());
		assertSame(carried, motion.moved().getFirst().cur());
		assertTrue(motion.vanished().isEmpty());
		assertTrue(motion.appeared().isEmpty());
	}

	@Test
	void differentSpritesNeverMatch() {
		GuiElement diamond = quad(0, 0, 0, 1);
		GuiElement apple = quad(0, 50, 50, 2);
		GuiMotion motion = GuiMotion.between(frame(diamond), frame(apple));
		assertTrue(motion.moved().isEmpty());
		assertEquals(List.of(diamond), motion.vanished());
		assertEquals(List.of(apple), motion.appeared());
	}

	@Test
	void aResizedScreenMatchesNothing() {
		GuiMotion motion = GuiMotion.between(frame(quad(0, 0, 0, 1)), new GuiFrame(List.of(quad(0, 5, 0, 1)), 200, 100));
		assertTrue(motion.isEmpty());
	}
}
