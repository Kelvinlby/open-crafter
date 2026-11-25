package mod.kelvinlby.opencrafter.transport.rpc;

import mod.kelvinlby.opencrafter.api.ThreadAffinity;
import mod.kelvinlby.opencrafter.api.handler.HandlerRegistry;
import mod.kelvinlby.opencrafter.transport.msgpack.MsgPackReader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.*;

class DispatcherTest {
	/** Collects tasks like Minecraft's queue; drained explicitly by the test ("next frame"). */
	private final Queue<Runnable> mainQueue = new ArrayDeque<>();
	private final Executor mainThread = mainQueue::add;
	private final HandlerRegistry handlers = new HandlerRegistry();
	private final List<Map<String, Object>> replies = new ArrayList<>();
	private final List<String> log = new ArrayList<>();
	private Dispatcher dispatcher;

	@BeforeEach
	void setUp() {
		handlers.register("echo", ThreadAffinity.ANY, args -> args.raw());
		handlers.register("main.mark", ThreadAffinity.MAIN, args -> {
			log.add("main:" + args.string("tag"));
			return args.string("tag");
		});
		handlers.register("any.mark", ThreadAffinity.ANY, args -> {
			log.add("any:" + args.string("tag"));
			return null;
		});
		handlers.register("fail", ThreadAffinity.ANY, args -> {
			throw new IllegalArgumentException("bad input");
		});
		handlers.register("npe", ThreadAffinity.MAIN, args -> {
			throw new NullPointerException();
		});
		handlers.register("unencodable", ThreadAffinity.ANY, args -> new Object());
		dispatcher = new Dispatcher(handlers, mainThread);
	}

	@SuppressWarnings("unchecked")
	private void dispatch(Map<String, Object> message) {
		dispatcher.dispatch(message, frame -> replies.add((Map<String, Object>) MsgPackReader.decode(frame, 4, frame.length - 4)));
	}

	private static Map<String, Object> request(Object id, String op, Map<String, Object> args) {
		Map<String, Object> message = new HashMap<>();
		message.put("id", id);
		message.put("op", op);
		message.put("args", args);
		return message;
	}

	private void drainMainThread() {
		Runnable task;
		while ((task = mainQueue.poll()) != null) {
			task.run();
		}
	}

	@Test
	void anyOpRunsInlineAndRepliesWithResult() {
		dispatch(request(1L, "echo", Map.of("x", 1L)));
		assertTrue(mainQueue.isEmpty());
		assertEquals(Map.of("id", 1L, "ok", true, "result", Map.of("x", 1L)), replies.getFirst());
	}

	@Test
	void mainOpIsQueuedAndRepliesAfterItRuns() {
		dispatch(request(2L, "main.mark", Map.of("tag", "a")));
		assertTrue(replies.isEmpty());
		assertEquals(1, mainQueue.size());
		drainMainThread();
		assertEquals(Map.of("id", 2L, "ok", true, "result", "a"), replies.getFirst());
	}

	@Test
	void mainOpsFromOneConnectionRunInArrivalOrder() {
		for (int i = 0; i < 50; i++) {
			dispatch(request((long) i, "main.mark", Map.of("tag", String.valueOf(i))));
		}
		drainMainThread();
		for (int i = 0; i < 50; i++) {
			assertEquals("main:" + i, log.get(i));
			assertEquals((long) i, replies.get(i).get("id"));
		}
	}

	@Test
	void handlerExceptionBecomesErrorReply() {
		dispatch(request(3L, "fail", Map.of()));
		assertEquals(Map.of("id", 3L, "ok", false, "error", "bad input"), replies.getFirst());
	}

	@Test
	void unexpectedExceptionOnMainThreadIsContained() {
		dispatch(request(4L, "npe", Map.of()));
		assertDoesNotThrow(this::drainMainThread);
		assertEquals(false, replies.getFirst().get("ok"));
		assertEquals("NullPointerException", replies.getFirst().get("error"));
	}

	@Test
	void unknownOpIsAnError() {
		dispatch(request(5L, "nope", Map.of()));
		assertEquals("Unknown op: nope", replies.getFirst().get("error"));
	}

	@Test
	void malformedRequestsAreErrors() {
		Map<String, Object> noOp = new HashMap<>(Map.of("id", 6L));
		dispatch(noOp);
		Map<String, Object> badArgs = new HashMap<>(Map.of("id", 7L, "op", "echo", "args", List.of()));
		dispatch(badArgs);
		assertEquals(6L, replies.get(0).get("id"));
		assertEquals(false, replies.get(0).get("ok"));
		assertEquals("'args' must be a map", replies.get(1).get("error"));
	}

	@Test
	void missingArgsDefaultToEmptyMap() {
		dispatch(new HashMap<>(Map.of("id", 8L, "op", "echo")));
		assertEquals(Map.of(), replies.getFirst().get("result"));
	}

	@Test
	void unencodableResultBecomesError() {
		dispatch(request(9L, "unencodable", Map.of()));
		assertEquals(false, replies.getFirst().get("ok"));
		assertTrue(((String) replies.getFirst().get("error")).startsWith("Result could not be encoded"));
	}

	@Test
	void fireAndForgetGetsNoReplyEvenOnError() {
		dispatch(request(null, "echo", Map.of()));
		dispatch(request(null, "fail", Map.of()));
		dispatch(request(null, "nope", Map.of()));
		assertTrue(replies.isEmpty());
	}

	@Test
	void futureResultRepliesOnCompletion() {
		CompletableFuture<Object> pending = new CompletableFuture<>();
		handlers.register("later", ThreadAffinity.ANY, args -> pending);
		dispatch(request(10L, "later", Map.of()));
		assertTrue(replies.isEmpty());
		pending.complete(List.of(1L, 2L));
		assertEquals(List.of(1L, 2L), replies.getFirst().get("result"));
	}

	@Test
	void failedFutureRepliesWithError() {
		handlers.register("laterFail", ThreadAffinity.ANY, args -> CompletableFuture.failedFuture(new IllegalStateException("gone")));
		dispatch(request(11L, "laterFail", Map.of()));
		assertEquals("gone", replies.getFirst().get("error"));
	}

	@Test
	void batchRunsAllCallsInOneMainThreadTaskWithPerCallEnvelopes() {
		List<Object> calls = List.of(
			Map.of("op", "main.mark", "args", Map.of("tag", "1")),
			Map.of("op", "any.mark", "args", Map.of("tag", "2")),
			Map.of("op", "fail"),
			Map.of("op", "nope"),
			Map.of("op", "main.mark", "args", Map.of("tag", "3")));
		dispatch(request(12L, "batch", Map.of("calls", calls)));
		assertEquals(1, mainQueue.size(), "the whole batch is one main-thread task");
		drainMainThread();
		assertEquals(List.of("main:1", "any:2", "main:3"), log);
		@SuppressWarnings("unchecked")
		List<Map<String, Object>> results = (List<Map<String, Object>>) replies.getFirst().get("result");
		assertEquals(true, replies.getFirst().get("ok"));
		assertEquals(5, results.size());
		assertEquals(Map.of("ok", true, "result", "1"), results.get(0));
		assertEquals(true, results.get(1).get("ok"));
		assertEquals(Map.of("ok", false, "error", "bad input"), results.get(2));
		assertEquals(Map.of("ok", false, "error", "Unknown op: nope"), results.get(3));
		assertEquals(Map.of("ok", true, "result", "3"), results.get(4));
	}

	@Test
	void batchOfAnyOpsRunsInline() {
		dispatch(request(13L, "batch", Map.of("calls", List.of(Map.of("op", "echo", "args", Map.of("a", 1L))))));
		assertTrue(mainQueue.isEmpty());
		assertEquals(1, replies.size());
	}

	@Test
	void batchWaitsForFutureResults() {
		CompletableFuture<Object> pending = new CompletableFuture<>();
		handlers.register("later", ThreadAffinity.ANY, args -> pending);
		dispatch(request(14L, "batch", Map.of("calls", List.of(Map.of("op", "later"), Map.of("op", "echo")))));
		assertTrue(replies.isEmpty());
		pending.complete("done");
		@SuppressWarnings("unchecked")
		List<Map<String, Object>> results = (List<Map<String, Object>>) replies.getFirst().get("result");
		assertEquals("done", results.getFirst().get("result"));
	}

	@Test
	void malformedBatchIsAnError() {
		dispatch(request(15L, "batch", Map.of()));
		dispatch(request(16L, "batch", Map.of("calls", List.of("x"))));
		assertEquals("batch needs a list 'calls'", replies.get(0).get("error"));
		assertEquals("calls[0] must be a map", replies.get(1).get("error"));
	}

	@Test
	void rejectedMainThreadExecutionIsAnError() {
		Dispatcher rejecting = new Dispatcher(handlers, task -> {
			throw new java.util.concurrent.RejectedExecutionException("stopping");
		});
		rejecting.dispatch(request(17L, "main.mark", Map.of("tag", "x")), frame -> replies.add(
			castMap(MsgPackReader.decode(frame, 4, frame.length - 4))));
		assertEquals("RejectedExecutionException: stopping", replies.getFirst().get("error"));
	}

	@SuppressWarnings("unchecked")
	private static Map<String, Object> castMap(Object value) {
		return (Map<String, Object>) value;
	}
}
