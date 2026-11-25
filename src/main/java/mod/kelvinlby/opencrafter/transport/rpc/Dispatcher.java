package mod.kelvinlby.opencrafter.transport.rpc;

import mod.kelvinlby.opencrafter.api.ThreadAffinity;
import mod.kelvinlby.opencrafter.api.handler.HandlerRegistry;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

/**
 * Routes requests to handlers.
 *
 * <p>{@link #dispatch} is called on a connection's reader thread, one message at a time in
 * arrival order. {@link ThreadAffinity#ANY} handlers run right there; {@link ThreadAffinity#MAIN}
 * handlers are submitted to the main-thread executor. Because each connection has a single
 * reader thread and {@code Minecraft.execute} appends to a FIFO queue, MAIN work from one
 * connection runs in arrival order. A batch is submitted as one task, so all of its calls
 * run within the same main-thread drain.
 */
public final class Dispatcher {
	public static final String BATCH_OP = "batch";

	private final HandlerRegistry handlers;
	private final Executor mainThread;
	private final LogThrottle throttle = new LogThrottle(TimeUnit.SECONDS.toNanos(1));

	public Dispatcher(HandlerRegistry handlers, Executor mainThread) {
		this.handlers = handlers;
		this.mainThread = mainThread;
	}

	public void dispatch(Map<String, Object> message, ReplySink sink) {
		Request request;
		try {
			request = Request.parse(message);
		} catch (IllegalArgumentException e) {
			new Reply(new Request(message.get("id"), "?", Map.of()), sink, throttle).fail(e);
			return;
		}
		Reply reply = new Reply(request, sink, throttle);
		if (BATCH_OP.equals(request.op())) {
			dispatchBatch(request, reply);
		} else {
			dispatchSingle(request, reply);
		}
	}

	private void dispatchSingle(Request request, Reply reply) {
		Optional<HandlerRegistry.Entry> found = handlers.find(request.op());
		if (found.isEmpty()) {
			reply.fail(new IllegalArgumentException("Unknown op: " + request.op()));
			return;
		}
		HandlerRegistry.Entry entry = found.get();
		schedule(entry.affinity(), reply, () -> Invocation.invoke(entry.handler(), request.args()));
	}

	private void dispatchBatch(Request request, Reply reply) {
		List<BatchCall> calls;
		try {
			calls = BatchCall.parseAll(request.args(), handlers);
		} catch (IllegalArgumentException e) {
			reply.fail(e);
			return;
		}
		ThreadAffinity affinity = BatchCall.anyNeedsMainThread(calls) ? ThreadAffinity.MAIN : ThreadAffinity.ANY;
		schedule(affinity, reply, () -> runInOrder(calls));
	}

	/** Starts every call in order; the result completes once all of them have. */
	private static CompletableFuture<List<Map<String, Object>>> runInOrder(List<BatchCall> calls) {
		List<CompletableFuture<Map<String, Object>>> outcomes = calls.stream().map(BatchCall::run).toList();
		return CompletableFuture.allOf(outcomes.toArray(CompletableFuture[]::new))
			.thenApply(done -> outcomes.stream().map(CompletableFuture::join).toList());
	}

	private void schedule(ThreadAffinity affinity, Reply reply, Work work) {
		Runnable task = () -> work.start().whenComplete(reply::deliver);
		if (affinity == ThreadAffinity.ANY) {
			task.run();
			return;
		}
		try {
			mainThread.execute(task);
		} catch (RejectedExecutionException e) {
			reply.fail(e);
		}
	}

	@FunctionalInterface
	private interface Work {
		CompletionStage<?> start();
	}
}
