package mod.kelvinlby.opencrafter.client.handview.anim;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StubSwingTest {
	private static final double FRAME = 0.05;

	@Test
	void restsAtZeroUntilEngaged() {
		StubSwing stub = new StubSwing();
		assertEquals(0, stub.progress(10), 0);
		assertFalse(stub.active());
	}

	@Test
	void trembleStaysBetweenThreeAndFivePercentWhileHeld() {
		StubSwing stub = new StubSwing();
		stub.engage(0);
		for (int tick = 1; tick <= 40; tick++) {
			stub.update(tick, true);
			if (tick >= 2) {
				for (double t = tick; t < tick + 1; t += FRAME) {
					float progress = stub.progress(t);
					assertTrue(progress >= 0.03 - 1e-6 && progress <= 0.05 + 1e-6, "progress " + progress + " at " + t);
				}
			}
		}
	}

	@Test
	void releaseEasesBackToZeroWithoutJumps() {
		StubSwing stub = new StubSwing();
		stub.engage(0);
		float last = 0;
		boolean released = false;
		for (int tick = 0; tick <= 20; tick++) {
			stub.update(tick, tick < 6);
			for (double t = tick; t < tick + 1; t += FRAME) {
				float progress = stub.progress(t);
				assertTrue(Math.abs(progress - last) < 0.01, "jump to " + progress + " at " + t);
				last = progress;
			}
			released |= !stub.active();
		}
		assertTrue(released);
		assertEquals(0, last, 0);
	}
}
