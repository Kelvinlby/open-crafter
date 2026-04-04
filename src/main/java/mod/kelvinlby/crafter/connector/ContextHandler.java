package mod.kelvinlby.crafter.connector;

import com.google.gson.JsonElement;

/**
 * Handler function for a registered command.
 * <p>
 * Receives a validated {@link CommandContext} and returns a result element,
 * or {@code null} for void responses.
 * </p>
 *
 * <h3>Example</h3>
 * <pre>
 * ContextHandler handler = ctx -> {
 *     double x = ctx.getDouble("x");
 *     double y = ctx.getDouble("y");
 *     double z = ctx.getDouble("z");
 *     // teleport logic...
 *     return null;
 * };
 * </pre>
 *
 * <p>Throw {@link CommandHandler.CommandException} to send a structured JSON-RPC
 * error back to the caller. Any other unchecked exception is caught by the
 * registry and converted to an internal-error response.</p>
 */
@FunctionalInterface
public interface ContextHandler {

    /**
     * Handles the command.
     *
     * @param ctx validated, named parameters
     * @return the result to include in the JSON-RPC response, or {@code null}
     * @throws CommandHandler.CommandException to return a structured error response
     * @throws Exception other exceptions are converted to internal error responses
     */
    JsonElement handle(CommandContext ctx) throws Exception;
}
