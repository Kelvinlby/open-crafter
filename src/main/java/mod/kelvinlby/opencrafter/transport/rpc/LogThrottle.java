package mod.kelvinlby.opencrafter.transport.rpc;

import java.util.concurrent.atomic.AtomicLong;

/** Lets at most one log line through per interval, counting what it suppressed. Thread-safe. */
public final class LogThrottle {
	private final long intervalNanos;
	private final AtomicLong nextAllowed = new AtomicLong(Long.MIN_VALUE);
	private final AtomicLong suppressed = new AtomicLong();

	public LogThrottle(long intervalNanos) {
		this.intervalNanos = intervalNanos;
	}

	/** Returns -1 to suppress, otherwise the number of lines suppressed since the last one. */
	public long tryAcquire() {
		long now = System.nanoTime();
		long allowed = nextAllowed.get();
		boolean due = allowed == Long.MIN_VALUE || now - allowed >= 0;
		if (!due || !nextAllowed.compareAndSet(allowed, now + intervalNanos)) {
			suppressed.incrementAndGet();
			return -1;
		}
		return suppressed.getAndSet(0);
	}
}
