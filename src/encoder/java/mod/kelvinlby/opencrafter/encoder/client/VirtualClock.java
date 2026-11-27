package mod.kelvinlby.opencrafter.encoder.client;

import net.minecraft.util.Util;

/**
 * The game's only clock ({@code Util.getNanos()}, which the tick timer, every animation and the
 * open-crafter modules read). It follows real time until the replay starts, then only moves when
 * the replay steps it, one frame at a time: however fast or slow frames render, everything on
 * screen advances at exactly the recorded pace.
 */
final class VirtualClock {
	private volatile boolean stepped;
	private volatile long frozenNanos;
	private long offset;

	/**
	 * Takes over {@code Util}'s time source, continuing from the game's current time (the game sets
	 * its own source while starting up, so this must run once the client is up).
	 */
	void install() {
		offset = Util.getNanos() - System.nanoTime();
		Util.setTimeSource(this::nanos);
	}

	long nanos() {
		return stepped ? frozenNanos : System.nanoTime() + offset;
	}

	/** From now on time stands still between {@link #advance} calls. */
	void freeze() {
		if (!stepped) {
			frozenNanos = nanos();
			stepped = true;
		}
	}

	void advance(long nanos) {
		frozenNanos += nanos;
	}
}
