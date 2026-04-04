package mod.kelvinlby.crafter.connector;


/**
 * Holds the {@link CommandException} type used across the command system.
 * <p>
 * Throw {@link CommandException} from a {@link ContextHandler} to return a
 * structured JSON-RPC error response to the caller.
 * </p>
 * <pre>
 * ctx -> {
 *     if (!someCondition) {
 *         throw new CommandHandler.CommandException("Condition not met");
 *     }
 *     // ...
 * }
 * </pre>
 */
public final class CommandHandler {

    private CommandHandler() {}

    /**
     * Exception thrown to indicate a command handling error.
     * Creates a structured JSON-RPC error response.
     */
    public static class CommandException extends Exception {
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
