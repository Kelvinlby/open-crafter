package mod.kelvinlby.opencrafter.client.handview.anim;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class OutAndBackTest {
	@Test
	void reachesOutHalfwayAndComesBack() {
		OutAndBack reach = new OutAndBack();
		reach.start(0, 6);
		assertEquals(0, reach.weight(0), 1e-6);
		assertEquals(1, reach.weight(3), 1e-6);
		assertEquals(0, reach.weight(6), 1e-6);
		reach.update(6);
		assertFalse(reach.active());
	}

	@Test
	void restartingMidReachContinuesFromTheSameWeight() {
		OutAndBack reach = new OutAndBack();
		reach.start(0, 6);
		float before = reach.weight(4.5);
		reach.start(4.5, 6);
		assertEquals(before, reach.weight(4.5), 1e-4);
	}
}
