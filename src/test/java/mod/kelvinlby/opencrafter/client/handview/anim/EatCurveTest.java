package mod.kelvinlby.opencrafter.client.handview.anim;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EatCurveTest {
	private static final int DURATION = 32;

	@Test
	void smoothCurveStartsAtRestAndArrivesInTheMiddle() {
		assertEquals(0, EatCurve.blend(DURATION, DURATION, true), 1e-6);
		assertEquals(1, EatCurve.blend(DURATION - EatCurve.SMOOTH_IN_TICKS, DURATION, true), 1e-6);
	}

	@Test
	void smoothCurveMovesLessThanVanillaInTheFirstTick() {
		float smooth = EatCurve.blend(DURATION - 1, DURATION, true);
		float vanilla = EatCurve.blend(DURATION - 1, DURATION, false);
		assertTrue(smooth < 0.15 && vanilla > 0.5, smooth + " vs " + vanilla);
	}

	@Test
	void bobFadesInFromItsStart() {
		assertEquals(0, EatCurve.smoothBobFade(EatCurve.BOB_START, DURATION), 1e-6);
		assertEquals(1, EatCurve.smoothBobFade(EatCurve.BOB_START - EatCurve.BOB_FADE_TICKS / DURATION, DURATION), 1e-6);
	}
}
