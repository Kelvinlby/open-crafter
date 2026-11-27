package mod.kelvinlby.opencrafter.encoder.client;

import org.jspecify.annotations.Nullable;

/** The replay cannot go on; the worker reports the job as failed. */
final class ReplayException extends RuntimeException {
	ReplayException(String message, @Nullable Throwable cause) {
		super(message, cause);
	}
}
