package mod.kelvinlby.crafter.connector;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;

/**
 * Functional interface for handling JSON-RPC commands.
 * 
 * <p>Implementations receive JSON-RPC parameters and return a result.</p>
 * 
 * <h3>Example Usage:</h3>
 * <pre>
 * // Simple handler with no args
 * CommandHandler getPosition = args -> {
 *     Vec3d pos = MinecraftClient.getInstance().player.getPos();
 *     return JsonRpcProtocol.JsonUtils.toJson(pos.toString());
 * };
 * 
 * // Handler with args
 * CommandHandler setBlock = args -> {
 *     int x = args.get(0).getAsInt();
 *     int y = args.get(1).getAsInt();
 *     int z = args.get(2).getAsInt();
 *     // ... set block logic
 *     return new JsonObject();
 * };
 * </pre>
 * 
 * <h3>Error Handling:</h3>
 * <p>Throw {@link CommandException} to return a structured error response.</p>
 * <pre>
 * CommandHandler handler = args -> {
 *     if (args.size() < 2) {
 *         throw new CommandException("Expected at least 2 arguments", -32602);
 *     }
 *     // ... handle command
 * };
 * </pre>
 */
@FunctionalInterface
public interface CommandHandler {

    /**
     * Handles a command with the given parameters.
     *
     * @param params the JSON-RPC parameters array
     * @return the result to send back (may be null for void responses)
     * @throws CommandException to return an error response
     * @throws Exception other exceptions will be converted to internal error responses
     */
    JsonElement handle(JsonArray params) throws Exception;

    /**
     * Exception thrown to indicate a command handling error.
     * Creates a structured JSON-RPC error response.
     */
    class CommandException extends Exception {
        private final int code;
        
        /**
         * Creates a command exception with default internal error code.
         */
        public CommandException(String message) {
            super(message);
            this.code = JsonRpcProtocol.ERROR_INTERNAL;
        }

        /**
         * Creates a command exception with custom error code.
         *
         * @param message the error message
         * @param code the JSON-RPC error code
         */
        public CommandException(String message, int code) {
            super(message);
            this.code = code;
        }

        /**
         * Creates a command exception for invalid parameters.
         */
        public static CommandException invalidParams(String message) {
            return new CommandException(message, JsonRpcProtocol.ERROR_INVALID_PARAMS);
        }

        /**
         * Creates a command exception for method not found.
         */
        public static CommandException methodNotFound(String method) {
            return new CommandException("Method not found: " + method, JsonRpcProtocol.ERROR_METHOD_NOT_FOUND);
        }

        public int getCode() {
            return code;
        }
    }
}
