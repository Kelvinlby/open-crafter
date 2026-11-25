package mod.kelvinlby.opencrafter.transport.rpc;

import mod.kelvinlby.opencrafter.transport.msgpack.MsgPackException;
import mod.kelvinlby.opencrafter.transport.wire.WireFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Turns the outcome of one request into a response frame (or a log line for
 * fire-and-forget requests). Encoding happens on the completing thread, so the result is
 * snapshotted before game state can change.
 */
final class Reply {
	private static final Logger LOGGER = LoggerFactory.getLogger("open-crafter/transport");

	private final Request request;
	private final ReplySink sink;
	private final LogThrottle throttle;

	Reply(Request request, ReplySink sink, LogThrottle throttle) {
		this.request = request;
		this.sink = sink;
		this.throttle = throttle;
	}

	void deliver(Object result, Throwable error) {
		if (error != null) {
			fail(Failures.unwrap(error));
		} else if (request.expectsReply()) {
			sink.send(encodeSuccess(result));
		}
	}

	void fail(Throwable error) {
		log(error);
		if (request.expectsReply()) {
			sink.send(WireFormat.failure(request.id(), Failures.describe(error)));
		}
	}

	private byte[] encodeSuccess(Object result) {
		try {
			return WireFormat.success(request.id(), result);
		} catch (MsgPackException | IllegalArgumentException e) {
			LOGGER.warn("Result of op '{}' could not be encoded: {}", request.op(), e.getMessage());
			return WireFormat.failure(request.id(), "Result could not be encoded: " + e.getMessage());
		}
	}

	/** Unexpected failures are logged with a stack trace; expected ones only when nobody gets a reply. */
	private void log(Throwable error) {
		boolean expected = Failures.isExpected(error);
		if (expected && request.expectsReply()) {
			LOGGER.debug("Op '{}' failed: {}", request.op(), error.getMessage());
			return;
		}
		long suppressed = throttle.tryAcquire();
		if (suppressed < 0) {
			return;
		}
		String suffix = suppressed > 0 ? " (" + suppressed + " similar messages suppressed)" : "";
		if (expected) {
			LOGGER.warn("Op '{}' failed: {}{}", request.op(), Failures.describe(error), suffix);
		} else {
			LOGGER.warn("Op '{}' threw{}", request.op(), suffix, error);
		}
	}
}
