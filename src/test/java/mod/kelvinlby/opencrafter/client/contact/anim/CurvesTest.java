package mod.kelvinlby.opencrafter.client.contact.anim;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CurvesTest {
	@Test
	void smoothStartsAndEndsAtRest() {
		assertEquals(0.0, Easing.smooth(-1), 0.0);
		assertEquals(0.5, Easing.smooth(0.5), 1.0e-9);
		assertEquals(1.0, Easing.smooth(2), 0.0);
		assertTrue(Easing.smooth(0.01) < 0.001);
	}

	@Test
	void pushGoesOutFastAndComesBack() {
		assertEquals(0.0, PushCurve.amount(0.0), 0.0);
		assertEquals(1.0, PushCurve.amount(PushCurve.OUT_SHARE), 1.0e-9);
		assertTrue(PushCurve.amount(0.1) > 0.5, "out quickly");
		assertTrue(PushCurve.amount(0.9) < 0.1, "back gently");
		assertEquals(0.0, PushCurve.amount(1.0), 0.0);
		double previous = 1.0;
		for (double t = PushCurve.OUT_SHARE; t <= 1.0; t += 0.05) {
			double amount = PushCurve.amount(t);
			assertTrue(amount <= previous + 1.0e-9);
			previous = amount;
		}
	}

	@Test
	void timelineStartsOnFirstUse() {
		Timeline timeline = new Timeline(4, 10);
		assertFalse(timeline.started());
		assertEquals(0.0, timeline.progress(10.3), 0.0);
		assertEquals(0.5, timeline.progress(12.3), 1.0e-9);
		assertFalse(timeline.finished(14.2));
		assertTrue(timeline.finished(14.3));
		assertEquals(1.0, timeline.progress(20), 0.0);
	}

	@Test
	void anUnseenTimelineStillEnds() {
		Timeline timeline = new Timeline(4, 10);
		assertFalse(timeline.finished(12));
		assertFalse(timeline.finished(10 + Timeline.UNSEEN_TICKS));
		assertTrue(timeline.finished(10 + Timeline.UNSEEN_TICKS + 4));
	}
}
