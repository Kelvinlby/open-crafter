package mod.kelvinlby.opencrafter.client.gaze.anim;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CriticalSpringTest {
	@Test
	void reachesAboutNinetyPercentWithinOneSettleTime() {
		CriticalSpring spring = new CriticalSpring();
		spring.step(10.0, 0.2, 0.2);
		assertEquals(10.0 * (1.0 - 5.0 * Math.exp(-4.0)), spring.value(), 1.0E-9);
		assertTrue(spring.value() > 9.0);
	}

	@Test
	void isFrameRateIndependent() {
		CriticalSpring oneStep = new CriticalSpring();
		oneStep.step(30.0, 0.15, 0.3);
		CriticalSpring manySteps = new CriticalSpring();
		for (int i = 0; i < 300; i++) {
			manySteps.step(30.0, 0.15, 0.001);
		}
		assertEquals(oneStep.value(), manySteps.value(), 1.0E-9);
		assertEquals(oneStep.velocity(), manySteps.velocity(), 1.0E-6);
	}

	@Test
	void neverOvershootsAFixedTarget() {
		CriticalSpring spring = new CriticalSpring();
		double previous = 0.0;
		for (int i = 0; i < 200; i++) {
			spring.step(45.0, 0.1, 1.0 / 60.0);
			assertTrue(spring.value() <= 45.0);
			assertTrue(spring.value() >= previous);
			previous = spring.value();
		}
	}

	@Test
	void retargetKeepsMotionContinuous() {
		CriticalSpring spring = new CriticalSpring();
		for (int i = 0; i < 5; i++) {
			spring.step(40.0, 0.2, 1.0 / 60.0);
		}
		double valueBefore = spring.value();
		double velocityBefore = spring.velocity();
		spring.step(-40.0, 0.2, 1.0E-6);
		assertEquals(valueBefore, spring.value(), 1.0E-3);
		assertEquals(velocityBefore, spring.velocity(), 1.0);
	}

	@Test
	void zeroSettleTimeJumps() {
		CriticalSpring spring = new CriticalSpring();
		spring.step(7.0, 0.0, 0.01);
		assertEquals(7.0, spring.value());
		assertEquals(0.0, spring.velocity());
	}
}
