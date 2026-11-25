package mod.kelvinlby.opencrafter.transport.server;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;

/**
 * Lock-free outbound queue of one connection; any thread may offer, one writer thread
 * drains. Offering never blocks.
 *
 * <p>Backpressure policy: replies are always queued unless the backlog exceeds
 * {@link #REPLY_BACKLOG_LIMIT} (the client stopped reading; the connection is then closed).
 * Events are dropped while the backlog exceeds {@link #EVENT_BACKLOG_LIMIT}. Frame
 * notifications are coalesced: only the newest pending one is kept.
 */
final class Outbox {
	static final long EVENT_BACKLOG_LIMIT = 4L << 20;
	static final long REPLY_BACKLOG_LIMIT = 256L << 20;
	private static final long PARK_NANOS = TimeUnit.MILLISECONDS.toNanos(100);

	private final ConcurrentLinkedQueue<byte[]> queue = new ConcurrentLinkedQueue<>();
	private final AtomicReference<byte[]> latestFrame = new AtomicReference<>();
	private final AtomicLong backlogBytes = new AtomicLong();
	private final AtomicLong droppedEvents = new AtomicLong();
	private volatile Thread writer;
	private volatile boolean closed;

	void bindWriter(Thread thread) {
		writer = thread;
	}

	/** @return false when the backlog is over the hard limit (caller should close) */
	boolean offerReply(byte[] frame) {
		if (backlogBytes.get() > REPLY_BACKLOG_LIMIT) {
			return false;
		}
		enqueue(frame);
		return true;
	}

	/** @return false when the event was dropped because the client is too slow */
	boolean offerEvent(byte[] frame) {
		if (backlogBytes.get() > EVENT_BACKLOG_LIMIT) {
			droppedEvents.incrementAndGet();
			return false;
		}
		enqueue(frame);
		return true;
	}

	/** Replaces any frame notification that has not been written yet. */
	void offerLatestFrame(byte[] frame) {
		latestFrame.set(frame);
		wakeWriter();
	}

	long droppedEvents() {
		return droppedEvents.get();
	}

	byte[] poll() {
		byte[] frame = queue.poll();
		if (frame != null) {
			backlogBytes.addAndGet(-frame.length);
		}
		return frame;
	}

	byte[] takeLatestFrame() {
		return latestFrame.getAndSet(null);
	}

	/** Parks the writer until something is offered or the outbox is closed. */
	void awaitWork() {
		while (!closed && queue.isEmpty() && latestFrame.get() == null) {
			LockSupport.parkNanos(this, PARK_NANOS);
		}
	}

	boolean isClosed() {
		return closed;
	}

	void close() {
		closed = true;
		wakeWriter();
	}

	private void enqueue(byte[] frame) {
		backlogBytes.addAndGet(frame.length);
		queue.add(frame);
		wakeWriter();
	}

	private void wakeWriter() {
		Thread thread = writer;
		if (thread != null) {
			LockSupport.unpark(thread);
		}
	}
}
