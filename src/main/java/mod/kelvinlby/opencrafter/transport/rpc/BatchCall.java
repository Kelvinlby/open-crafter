package mod.kelvinlby.opencrafter.transport.rpc;

import mod.kelvinlby.opencrafter.api.ThreadAffinity;
import mod.kelvinlby.opencrafter.api.handler.HandlerRegistry;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * One element of a {@code batch}. Its outcome is an envelope
 * {@code {"ok": true, "result": ...}} or {@code {"ok": false, "error": ...}}.
 */
record BatchCall(String op, Map<String, Object> args, HandlerRegistry.Entry entry) {
	/** @throws IllegalArgumentException when {@code calls} is missing or an element is malformed */
	static List<BatchCall> parseAll(Map<String, Object> batchArgs, HandlerRegistry handlers) {
		if (!(batchArgs.get("calls") instanceof List<?> calls)) {
			throw new IllegalArgumentException("batch needs a list 'calls'");
		}
		List<BatchCall> parsed = new ArrayList<>(calls.size());
		for (int i = 0; i < calls.size(); i++) {
			parsed.add(parse(calls.get(i), i, handlers));
		}
		return parsed;
	}

	static boolean anyNeedsMainThread(List<BatchCall> calls) {
		return calls.stream().anyMatch(call -> call.entry != null && call.entry.affinity() == ThreadAffinity.MAIN);
	}

	private static BatchCall parse(Object call, int index, HandlerRegistry handlers) {
		if (!(call instanceof Map<?, ?> map)) {
			throw new IllegalArgumentException("calls[" + index + "] must be a map");
		}
		try {
			String op = Request.parseOp(map.get("op"));
			return new BatchCall(op, Request.parseArgs(map.get("args")), handlers.find(op).orElse(null));
		} catch (IllegalArgumentException e) {
			throw new IllegalArgumentException("calls[" + index + "]: " + e.getMessage());
		}
	}

	CompletableFuture<Map<String, Object>> run() {
		if (entry == null) {
			return CompletableFuture.completedFuture(failure("Unknown op: " + op));
		}
		return Invocation.invoke(entry.handler(), args)
			.handle((result, error) -> error == null ? success(result) : failure(Failures.describe(Failures.unwrap(error))))
			.toCompletableFuture();
	}

	private static Map<String, Object> success(Object result) {
		Map<String, Object> envelope = HashMap.newHashMap(2);
		envelope.put("ok", true);
		envelope.put("result", result);
		return envelope;
	}

	private static Map<String, Object> failure(String error) {
		Map<String, Object> envelope = HashMap.newHashMap(2);
		envelope.put("ok", false);
		envelope.put("error", error);
		return envelope;
	}
}
