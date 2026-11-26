package mod.kelvinlby.opencrafter.client.handview.anim;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EatReturnTest {
	@Test
	void restsUntilReleased() {
		assertEquals(0, new EatReturn().level(100), 1e-9);
	}

	@Test
	void startsWhereThePoseWasAndEndsAtRest() {
		EatReturn motion = new EatReturn();
		motion.release(10, 0.8, 0.05);
		assertEquals(0.8, motion.level(10), 1e-9);
		assertEquals(0.05, motion.bob(10), 1e-9);
		assertEquals(0, motion.level(10 + EatReturn.RETURN_TICKS), 1e-9);
		assertEquals(0, motion.bob(10 + EatReturn.RETURN_TICKS + 3), 1e-9);
	}

	@Test
	void movesBackSteadily() {
		EatReturn motion = new EatReturn();
		motion.release(0, 1, 0);
		double previous = motion.level(0);
		for (double t = 0.25; t <= EatReturn.RETURN_TICKS; t += 0.25) {
			double level = motion.level(t);
			assertTrue(level <= previous, "rose at " + t);
			assertTrue(previous - level < 0.1, "jumped at " + t);
			previous = level;
		}
	}
}
