package mod.kelvinlby.opencrafter.client.interaction.capture;

import mod.kelvinlby.opencrafter.client.interaction.outcome.PreAction;
import org.jspecify.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * The {@code MultiPlayerGameMode} calls in progress, each with the world as it was when the call
 * started. Packets sent inside a call are attributed to it; calls can nest (a break aborting another).
 */
final class GameModeScope {
	private final Deque<PreAction> calls = new ArrayDeque<>();

	void enter(PreAction before) {
		calls.push(before);
	}

	void exit() {
		calls.pollFirst();
	}

	/** The innermost call's snapshot, or null when the packet was sent outside the game mode. */
	@Nullable PreAction current() {
		return calls.peekFirst();
	}

	void clear() {
		calls.clear();
	}
}
