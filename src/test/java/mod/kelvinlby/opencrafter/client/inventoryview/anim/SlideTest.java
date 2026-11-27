package mod.kelvinlby.opencrafter.client.inventoryview.anim;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SlideTest {
	@Test
	void movesLinearlyAndStopsAtTheTarget() {
		Slide slide = new Slide();
		slide.moveTo(10, 1, 4);
		assertEquals(0, slide.progress(10), 1e-9);
		assertEquals(0.5, slide.progress(12), 1e-9);
		assertFalse(slide.settled(12));
		assertEquals(1, slide.progress(20), 1e-9);
		assertTrue(slide.settled(20));
	}

	@Test
	void reversingTurnsAroundWhereItIs() {
		Slide slide = new Slide();
		slide.moveTo(0, 1, 4);
		slide.moveTo(3, 0, 4);
		assertEquals(0.75, slide.progress(3), 1e-9);
		assertEquals(0.25, slide.progress(5), 1e-9);
		assertEquals(0, slide.progress(100), 1e-9);
	}

	@Test
	void jumpStandsStill() {
		Slide slide = new Slide();
		slide.jumpTo(1);
		assertEquals(1, slide.progress(50), 1e-9);
		assertTrue(slide.settled(50));
	}
}
