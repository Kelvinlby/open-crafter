package mod.kelvinlby.opencrafter.encoder.translate;

import org.jspecify.annotations.Nullable;

/** A packet Via could not translate (malformed, or a gap in its protocol support). */
public final class TranslationException extends RuntimeException {
	TranslationException(String message, @Nullable Throwable cause) {
		super(message, cause);
	}
}
