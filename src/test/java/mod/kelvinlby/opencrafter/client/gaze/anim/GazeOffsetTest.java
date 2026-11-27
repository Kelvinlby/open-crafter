package mod.kelvinlby.opencrafter.client.gaze.anim;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GazeOffsetTest {
	private static final double FRAME = 1.0 / 60.0;

	@Test
	void startsAtHome() {
		assertTrue(new GazeOffset().isHome());
	}

	@Test
	void turnsAcrossTheSeamTheShortWay() {
		GazeOffset offset = new GazeOffset();
		offset.easeToward(170.0, 0.0, 0.1, 1.0);
		assertEquals(170.0, offset.yaw(), 1.0E-3);
		// 190 is -170: 20 degrees on, never 340 back
		for (int i = 0; i < 120; i++) {
			offset.easeToward(-170.0, 0.0, 0.1, FRAME);
			assertTrue(Math.abs(offset.yaw()) >= 169.0, "went the long way: " + offset.yaw());
		}
		assertEquals(-170.0, offset.yaw(), 1.0E-3);
	}

	@Test
	void yawStaysWithinHalfTurn() {
		GazeOffset offset = new GazeOffset();
		for (int i = 0; i < 500; i++) {
			offset.easeToward(i * 7.0, 0.0, 0.05, FRAME);
			assertTrue(offset.yaw() >= -180.0 && offset.yaw() < 180.0);
		}
	}

	@Test
	void easesHomeToExactlyZero() {
		GazeOffset offset = new GazeOffset();
		offset.easeToward(30.0, -10.0, 0.1, 1.0);
		assertFalse(offset.isHome());
		for (int i = 0; i < 600 && !offset.isHome(); i++) {
			offset.easeHome(0.2, FRAME);
		}
		assertTrue(offset.isHome());
		assertEquals(0.0, offset.yaw());
		assertEquals(0.0, offset.pitch());
	}

	@Test
	void homeIsGradualNotAJump() {
		GazeOffset offset = new GazeOffset();
		offset.easeToward(40.0, 0.0, 0.1, 1.0);
		double previous = offset.yaw();
		for (int i = 0; i < 30; i++) {
			offset.easeHome(0.25, FRAME);
			assertTrue(previous - offset.yaw() < 5.0, "jumped by " + (previous - offset.yaw()));
			previous = offset.yaw();
		}
		assertTrue(offset.yaw() > 0.0);
	}
}
