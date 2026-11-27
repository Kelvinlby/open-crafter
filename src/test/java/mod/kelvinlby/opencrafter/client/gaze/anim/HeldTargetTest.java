package mod.kelvinlby.opencrafter.client.gaze.anim;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class HeldTargetTest {
	private static final long HOLD = 100L;

	@Test
	void holdsUntilOutcomeThenForTheHoldTime() {
		HeldTarget<String> held = new HeldTarget<>();
		held.holdUntilOutcome("a", 1L);
		assertEquals("a", held.active(1_000_000L, HOLD));
		held.onOutcome(1L, 2_000_000L);
		assertEquals("a", held.active(2_000_000L + HOLD, HOLD));
		assertNull(held.active(2_000_000L + HOLD + 1, HOLD));
	}

	@Test
	void outcomeOfAReplacedActionIsIgnored() {
		HeldTarget<String> held = new HeldTarget<>();
		held.holdUntilOutcome("a", 1L);
		held.holdUntilOutcome("b", 2L);
		held.onOutcome(1L, 0L);
		assertEquals("b", held.active(10_000L, HOLD));
	}

	@Test
	void holdFromExpiresAfterHold() {
		HeldTarget<String> held = new HeldTarget<>();
		held.holdFrom("spoof", 500L);
		assertEquals("spoof", held.active(500L + HOLD, HOLD));
		held.holdFrom("spoof", 550L);
		assertEquals("spoof", held.active(550L + HOLD, HOLD));
		assertNull(held.active(551L + HOLD, HOLD));
	}

	@Test
	void newerTargetReplacesAtOnce() {
		HeldTarget<String> held = new HeldTarget<>();
		held.holdFrom("old", 0L);
		held.holdUntilOutcome("new", 3L);
		assertEquals("new", held.active(1_000L, HOLD));
	}
}
