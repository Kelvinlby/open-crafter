package mod.kelvinlby.opencrafter.client.input.hold;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Which simulated keys are currently held, by key id. Pure logic, no game classes.
 *
 * <p>A key can be held by three independent sources; it counts as held while any of them is active:
 * <ul>
 *   <li><b>stream</b>: declarative {@code input.keys} state, a lease renewed by every message;</li>
 *   <li><b>explicit</b>: {@code input.key down}, held until a wall-clock deadline;</li>
 *   <li><b>pulse</b>: {@code input.key press}, held until a client tick number.</li>
 * </ul>
 * Times are milliseconds, ticks are a monotonically increasing client tick counter.
 */
public final class HoldTable {
	private static final long NONE = Long.MIN_VALUE;

	private static final class Hold {
		long streamUntil = NONE;
		long explicitUntil = NONE;
		long pulseUntilTick = NONE;

		boolean active(long nowMillis, long tick) {
			return streamUntil > nowMillis || explicitUntil > nowMillis || pulseUntilTick > tick;
		}
	}

	private final Map<String, Hold> holds = new LinkedHashMap<>();

	/** Replaces the streamed set: listed keys are leased until {@code untilMillis}, other streamed holds end. */
	public void stream(Collection<String> keys, long untilMillis) {
		holds.values().forEach(hold -> hold.streamUntil = NONE);
		keys.forEach(key -> hold(key).streamUntil = untilMillis);
	}

	/** Holds a key until {@code untilMillis}, replacing any earlier explicit deadline. */
	public void holdUntil(String key, long untilMillis) {
		hold(key).explicitUntil = untilMillis;
	}

	/** Holds a key while the tick counter is below {@code untilTick}. */
	public void pulse(String key, long untilTick) {
		hold(key).pulseUntilTick = untilTick;
	}

	/** Ends every source holding this key. */
	public void release(String key) {
		holds.remove(key);
	}

	public void releaseAll() {
		holds.clear();
	}

	/** Drops expired holds and returns the keys that are still held. */
	public Set<String> held(long nowMillis, long tick) {
		holds.values().removeIf(hold -> !hold.active(nowMillis, tick));
		return Set.copyOf(holds.keySet());
	}

	private Hold hold(String key) {
		return holds.computeIfAbsent(key, k -> new Hold());
	}
}
