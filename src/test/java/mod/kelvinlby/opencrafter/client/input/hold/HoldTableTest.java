package mod.kelvinlby.opencrafter.client.input.hold;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HoldTableTest {
	private final HoldTable table = new HoldTable();

	@Test
	void streamedKeyIsHeldUntilLeaseExpires() {
		table.stream(List.of("a"), 200);
		assertEquals(Set.of("a"), table.held(199, 0));
		assertEquals(Set.of(), table.held(200, 0));
	}

	@Test
	void streamRenewsListedAndReleasesUnlisted() {
		table.stream(List.of("a", "b"), 200);
		table.stream(List.of("b"), 400);
		assertEquals(Set.of("b"), table.held(100, 0));
		assertEquals(Set.of("b"), table.held(300, 0));
	}

	@Test
	void streamDoesNotReleaseExplicitHolds() {
		table.holdUntil("a", 1000);
		table.stream(List.of("b"), 200);
		table.stream(List.of(), 300);
		assertEquals(Set.of("a"), table.held(250, 0));
	}

	@Test
	void keyHeldBySeveralSourcesStaysUntilAllEnd() {
		table.holdUntil("a", 500);
		table.stream(List.of("a"), 200);
		table.stream(List.of(), 300);
		assertEquals(Set.of("a"), table.held(400, 0));
		assertEquals(Set.of(), table.held(500, 0));
	}

	@Test
	void explicitHoldReplacesEarlierDeadline() {
		table.holdUntil("a", 5000);
		table.holdUntil("a", 100);
		assertEquals(Set.of(), table.held(100, 0));
	}

	@Test
	void pulseIsHeldUntilTick() {
		table.pulse("a", 12);
		assertEquals(Set.of("a"), table.held(0, 11));
		assertEquals(Set.of(), table.held(0, 12));
	}

	@Test
	void releaseEndsEverySource() {
		table.holdUntil("a", 1000);
		table.stream(List.of("a"), 1000);
		table.pulse("a", 100);
		table.release("a");
		assertEquals(Set.of(), table.held(0, 0));
	}

	@Test
	void releaseAllClearsEverything() {
		table.holdUntil("a", 1000);
		table.stream(List.of("b"), 1000);
		table.releaseAll();
		assertEquals(Set.of(), table.held(0, 0));
	}
}
