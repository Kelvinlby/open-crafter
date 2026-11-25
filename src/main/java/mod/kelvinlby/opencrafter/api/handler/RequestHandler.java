package mod.kelvinlby.opencrafter.api.handler;

import mod.kelvinlby.opencrafter.api.Args;

/**
 * Handles one backend operation ("op").
 *
 * <p>The returned value is sent back to the backend and must be MessagePack-encodable:
 * {@code null}, {@link Boolean}, {@link Number}, {@link String}, {@code byte[]},
 * {@link java.util.List}, {@link java.util.Map} (String keys), or a
 * {@link java.util.concurrent.CompletableFuture} completing with one of those.
 * Throwing reports an error to the backend; it never crashes the game.
 */
@FunctionalInterface
public interface RequestHandler {
	Object handle(Args args) throws Exception;
}
