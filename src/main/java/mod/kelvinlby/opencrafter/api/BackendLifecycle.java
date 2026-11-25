package mod.kelvinlby.opencrafter.api;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** Notifies modules when the last backend connection goes away (e.g. to release held keys). */
public final class BackendLifecycle {
	private final List<Runnable> lastDisconnectListeners = new CopyOnWriteArrayList<>();

	/** {@code listener} runs on a transport thread; hop to the main thread before touching game state. */
	public void onLastDisconnect(Runnable listener) {
		lastDisconnectListeners.add(listener);
	}

	public void fireLastDisconnect() {
		lastDisconnectListeners.forEach(Runnable::run);
	}
}
