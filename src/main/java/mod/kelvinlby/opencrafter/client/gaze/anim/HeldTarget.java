package mod.kelvinlby.opencrafter.client.gaze.anim;

import org.jspecify.annotations.Nullable;

/**
 * The latest thing the gaze looks at and how long it stays: until a pending action's outcome plus
 * the hold time, or the hold time from a given moment. A newer target replaces the old one at once
 * (the easing makes that a smooth retarget). Times are {@code System.nanoTime()}.
 */
public final class HeldTarget<T> {
	private static final long NO_ACTION = Long.MIN_VALUE;

	private @Nullable T target;
	private long actionId = NO_ACTION;
	private boolean released;
	private long releasedNanos;

	/** Looks at {@code newTarget} until {@link #onOutcome} for action {@code id}, then for the hold time. */
	public void holdUntilOutcome(T newTarget, long id) {
		target = newTarget;
		actionId = id;
		released = false;
	}

	/** Looks at {@code newTarget} for the hold time from {@code nowNanos}. */
	public void holdFrom(T newTarget, long nowNanos) {
		target = newTarget;
		actionId = NO_ACTION;
		release(nowNanos);
	}

	/** An action's outcome: starts the hold when that action is the one looked at. */
	public void onOutcome(long id, long nowNanos) {
		if (target != null && !released && actionId == id) {
			release(nowNanos);
		}
	}

	/** The target still looked at, or null once its hold ran out. */
	public @Nullable T active(long nowNanos, long holdNanos) {
		if (target != null && released && nowNanos - releasedNanos > holdNanos) {
			clear();
		}
		return target;
	}

	public void clear() {
		target = null;
		actionId = NO_ACTION;
		released = false;
	}

	private void release(long nowNanos) {
		released = true;
		releasedNanos = nowNanos;
	}
}
