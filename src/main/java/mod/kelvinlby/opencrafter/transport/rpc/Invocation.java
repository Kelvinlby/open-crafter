package mod.kelvinlby.opencrafter.transport.rpc;

import mod.kelvinlby.opencrafter.api.Args;
import mod.kelvinlby.opencrafter.api.handler.RequestHandler;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/** Runs one handler and normalises its outcome into a stage; never throws (except fatal errors). */
final class Invocation {
	private Invocation() {
	}

	static CompletionStage<?> invoke(RequestHandler handler, Map<String, Object> args) {
		try {
			Object result = handler.handle(new Args(args));
			return result instanceof CompletionStage<?> stage ? stage : CompletableFuture.completedFuture(result);
		} catch (Throwable error) {
			Failures.rethrowIfFatal(error);
			return CompletableFuture.failedFuture(error);
		}
	}
}
