package mod.kelvinlby.opencrafter.api;

/** Where a request handler must run. */
public enum ThreadAffinity {
	/** Runs on the game (client) main thread; queued without blocking the caller. */
	MAIN,
	/** Runs directly on the transport thread; must not touch game state. */
	ANY
}
