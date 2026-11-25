package mod.kelvinlby.opencrafter.transport.rpc;

import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;

/** Classifies and describes handler failures. */
public final class Failures {
	private Failures() {
	}

	/** Errors the JVM cannot recover from; everything else is reported to the backend. */
	public static boolean isFatal(Throwable error) {
		return error instanceof VirtualMachineError && !(error instanceof StackOverflowError);
	}

	public static void rethrowIfFatal(Throwable error) {
		if (isFatal(error)) {
			throw (VirtualMachineError) error;
		}
	}

	/** Strips future wrappers so the backend sees the handler's own exception. */
	public static Throwable unwrap(Throwable error) {
		Throwable current = error;
		while ((current instanceof CompletionException || current instanceof ExecutionException) && current.getCause() != null) {
			current = current.getCause();
		}
		return current;
	}

	/** Validation-style failures caused by the request, not by a bug. */
	public static boolean isExpected(Throwable error) {
		return error instanceof IllegalArgumentException
			|| error instanceof IllegalStateException
			|| error instanceof UnsupportedOperationException;
	}

	/** Message sent to the backend: the plain message for expected failures, else "Type: message". */
	public static String describe(Throwable error) {
		String message = error.getMessage();
		if (isExpected(error) && message != null) {
			return message;
		}
		String type = error.getClass().getSimpleName();
		return message == null ? type : type + ": " + message;
	}
}
