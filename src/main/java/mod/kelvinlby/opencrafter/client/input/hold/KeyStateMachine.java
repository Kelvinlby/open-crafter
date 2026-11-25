package mod.kelvinlby.opencrafter.client.input.hold;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Turns the set of simulated held keys into the same state changes a physical key would
 * cause, mirroring vanilla's {@code KeyboardHandler} / {@code MouseHandler}. Pure logic; the
 * game side is reached through {@link Port}.
 *
 * <ul>
 *   <li>Press transition while the game takes input: {@code setDown(true)} plus one click.</li>
 *   <li>Press transition while a screen is open: the bound key is delivered to the screen
 *       (like a real key press) and the mapping is not touched.</li>
 *   <li>Release transition: {@code setDown(false)}, unless the physical key is still held.</li>
 *   <li>While held and the game takes input, a non-toggle mapping that something else reset
 *       (screen open/close, {@code KeyMapping.setAll}, the user releasing the same physical key)
 *       is put down again without a new click.</li>
 * </ul>
 * Toggle-mode mappings only ever see press/release transitions, so a hold toggles exactly once.
 */
public final class KeyStateMachine {
	/** Game-side operations on a key mapping, addressed by id. Unknown ids must be no-ops. */
	public interface Port {
		boolean isDown(String key);

		void setDown(String key, boolean down);

		void click(String key);

		/** True when the mapping currently toggles on press instead of following the key. */
		boolean isToggleMode(String key);

		boolean isPhysicallyHeld(String key);

		/** Sends the mapping's bound key to the open screen as a real press + release would. */
		void deliverToScreen(String key);
	}

	private final Port port;
	private Set<String> previous = Set.of();
	private final Set<String> applied = new HashSet<>();

	public KeyStateMachine(Port port) {
		this.port = port;
	}

	/** Applies the transitions from the previous held set to {@code held}. */
	public void reconcile(Set<String> held, boolean gameTakesInput) {
		List<String> toScreen = new ArrayList<>();
		for (String key : previous) {
			if (!held.contains(key)) {
				releaseKey(key);
			}
		}
		for (String key : held) {
			if (!previous.contains(key)) {
				pressKey(key, gameTakesInput, toScreen);
			} else {
				maintainKey(key, gameTakesInput);
			}
		}
		previous = Set.copyOf(held);
		toScreen.forEach(port::deliverToScreen);
	}

	/** A press of a key that is already held: like a release and press too fast to observe. */
	public void repress(String key, boolean gameTakesInput) {
		if (!gameTakesInput) {
			port.deliverToScreen(key);
			return;
		}
		if (port.isToggleMode(key)) {
			port.setDown(key, true);
		}
		port.click(key);
		applied.add(key);
	}

	/** Releases everything this machine pressed and forgets all state. */
	public void releaseAll() {
		applied.forEach(this::setUp);
		applied.clear();
		previous = Set.of();
	}

	public boolean isHeld(String key) {
		return previous.contains(key);
	}

	/** True when the mapping is down because of this machine (not suspended by a screen). */
	public boolean isApplied(String key) {
		return applied.contains(key);
	}

	private void pressKey(String key, boolean gameTakesInput, List<String> toScreen) {
		if (!gameTakesInput) {
			toScreen.add(key);
			return;
		}
		port.setDown(key, true);
		port.click(key);
		applied.add(key);
	}

	private void maintainKey(String key, boolean gameTakesInput) {
		if (port.isToggleMode(key)) {
			return;
		}
		if (!gameTakesInput) {
			applied.remove(key);
			return;
		}
		if (!port.isDown(key)) {
			port.setDown(key, true);
		}
		applied.add(key);
	}

	private void releaseKey(String key) {
		if (applied.remove(key)) {
			setUp(key);
		}
	}

	private void setUp(String key) {
		if (port.isToggleMode(key) || !port.isPhysicallyHeld(key)) {
			port.setDown(key, false);
		}
	}
}
