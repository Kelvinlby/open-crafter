package mod.kelvinlby.opencrafter.client.input.hold;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KeyStateMachineTest {
	/** Mimics KeyMapping / ToggleKeyMapping: setDown toggles on true when in toggle mode. */
	private static final class FakePort implements KeyStateMachine.Port {
		final Map<String, Boolean> down = new HashMap<>();
		final Map<String, Integer> clicks = new HashMap<>();
		final Set<String> toggles = new HashSet<>();
		final Set<String> physical = new HashSet<>();
		final List<String> screenPresses = new ArrayList<>();

		@Override
		public boolean isDown(String key) {
			return down.getOrDefault(key, false);
		}

		@Override
		public void setDown(String key, boolean value) {
			if (toggles.contains(key)) {
				if (value) {
					down.put(key, !isDown(key));
				}
			} else {
				down.put(key, value);
			}
		}

		@Override
		public void click(String key) {
			clicks.merge(key, 1, Integer::sum);
		}

		@Override
		public boolean isToggleMode(String key) {
			return toggles.contains(key);
		}

		@Override
		public boolean isPhysicallyHeld(String key) {
			return physical.contains(key);
		}

		@Override
		public void deliverToScreen(String key) {
			screenPresses.add(key);
		}

		int clicks(String key) {
			return clicks.getOrDefault(key, 0);
		}

		/** What vanilla KeyMapping.releaseAll does when a screen opens. */
		void releaseAllLikeVanilla() {
			down.replaceAll((key, value) -> false);
			clicks.clear();
		}
	}

	private final FakePort port = new FakePort();
	private final KeyStateMachine machine = new KeyStateMachine(port);

	@Test
	void holdProducesOneClickAndStaysDown() {
		for (int frame = 0; frame < 5; frame++) {
			machine.reconcile(Set.of("w"), true);
		}
		assertTrue(port.isDown("w"));
		assertEquals(1, port.clicks("w"));
	}

	@Test
	void releaseTransitionPutsKeyUp() {
		machine.reconcile(Set.of("w"), true);
		machine.reconcile(Set.of(), true);
		assertFalse(port.isDown("w"));
	}

	@Test
	void releaseKeepsKeyDownWhileUserPhysicallyHoldsIt() {
		machine.reconcile(Set.of("w"), true);
		port.physical.add("w");
		machine.reconcile(Set.of(), true);
		assertTrue(port.isDown("w"));
	}

	@Test
	void externalResetIsReassertedWithoutNewClick() {
		machine.reconcile(Set.of("w"), true);
		port.setDown("w", false);
		machine.reconcile(Set.of("w"), true);
		assertTrue(port.isDown("w"));
		assertEquals(1, port.clicks("w"));
	}

	@Test
	void holdSurvivesScreenAndResumesWithoutClick() {
		machine.reconcile(Set.of("w"), true);
		port.releaseAllLikeVanilla();
		machine.reconcile(Set.of("w"), false);
		assertFalse(port.isDown("w"));
		machine.reconcile(Set.of("w"), true);
		assertTrue(port.isDown("w"));
		assertEquals(0, port.clicks("w"));
	}

	@Test
	void pressWhileScreenOpenGoesToScreen() {
		machine.reconcile(Set.of("e"), false);
		assertEquals(List.of("e"), port.screenPresses);
		assertFalse(port.isDown("e"));
		assertEquals(0, port.clicks("e"));
	}

	@Test
	void toggleHoldTogglesOnceAndReleaseKeepsState() {
		port.toggles.add("sneak");
		machine.reconcile(Set.of("sneak"), true);
		machine.reconcile(Set.of("sneak"), true);
		machine.reconcile(Set.of(), true);
		assertTrue(port.isDown("sneak"));
		machine.reconcile(Set.of("sneak"), true);
		assertFalse(port.isDown("sneak"));
	}

	@Test
	void toggleIsNotReassertedAfterExternalReset() {
		port.toggles.add("sneak");
		machine.reconcile(Set.of("sneak"), true);
		port.down.put("sneak", false);
		machine.reconcile(Set.of("sneak"), true);
		assertFalse(port.isDown("sneak"));
	}

	@Test
	void repressAddsClickWhileStayingDown() {
		machine.reconcile(Set.of("q"), true);
		machine.repress("q", true);
		assertTrue(port.isDown("q"));
		assertEquals(2, port.clicks("q"));
	}

	@Test
	void repressOfToggleFlipsIt() {
		port.toggles.add("sneak");
		machine.reconcile(Set.of("sneak"), true);
		machine.repress("sneak", true);
		assertFalse(port.isDown("sneak"));
	}

	@Test
	void releaseAllPutsAppliedKeysUp() {
		machine.reconcile(Set.of("w", "a"), true);
		machine.releaseAll();
		assertFalse(port.isDown("w"));
		assertFalse(port.isDown("a"));
		assertFalse(machine.isHeld("w"));
	}

	@Test
	void releaseAfterScreenSuspendedHoldDoesNotTouchMapping() {
		machine.reconcile(Set.of("w"), true);
		port.releaseAllLikeVanilla();
		machine.reconcile(Set.of("w"), false);
		port.down.put("w", true);
		machine.reconcile(Set.of(), false);
		assertTrue(port.isDown("w"));
	}
}
