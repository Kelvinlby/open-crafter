package mod.kelvinlby.crafter.connector;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

/**
 * JSON-RPC 2.0 protocol implementation.
 * <p>
 * Provides request/response parsing and formatting for JSON-RPC 2.0 messages.
 * </p>
 * 
 * Request format:
 * <pre>
 * {
 *   "jsonrpc": "2.0",
 *   "method": "command_name",
 *   "params": [...],
 *   "id": 1
 * }
 * </pre>
 * 
 * Response format (success):
 * <pre>
 * {
 *   "jsonrpc": "2.0",
 *   "result": {...},
 *   "id": 1
 * }
 * </pre>
 * 
 * Response format (error):
 * <pre>
 * {
 *   "jsonrpc": "2.0",
 *   "error": {
 *     "code": -32600,
 *     "message": "Invalid Request"
 *   },
 *   "id": 1
 * }
 * </pre>
 */
public final class JsonRpcProtocol {

    public static final String VERSION = "2.0";
    
    // Standard JSON-RPC 2.0 error codes
    public static final int ERROR_PARSE = -32700;
    public static final int ERROR_INVALID_REQUEST = -32600;
    public static final int ERROR_METHOD_NOT_FOUND = -32601;
    public static final int ERROR_INVALID_PARAMS = -32602;
    public static final int ERROR_INTERNAL = -32603;

    private JsonRpcProtocol() {
        // Utility class
    }

    /**
     * Parses a JSON-RPC request string into a request object.
     *
     * @param message the raw JSON message
     * @return parsed request or null if parsing fails
     */
    public static RpcRequest parseRequest(String message) {
        try {
            JsonElement element = JsonUtils.GSON.fromJson(message, JsonElement.class);
            if (!element.isJsonObject()) {
                return null;
            }

            JsonObject obj = element.getAsJsonObject();
            
            // Validate jsonrpc version
            if (!obj.has("jsonrpc") || !obj.get("jsonrpc").getAsString().equals(VERSION)) {
                return createInvalidVersionRequest();
            }

            // Method is required
            if (!obj.has("method")) {
                return createInvalidRequest("Method not specified");
            }

            String method = obj.get("method").getAsString();
            JsonArray params = obj.has("params") && obj.get("params").isJsonArray() 
                ? obj.getAsJsonArray("params") 
                : new JsonArray();
            
            JsonElement id = obj.has("id") ? obj.get("id") : null;

            return new RpcRequest(method, params, id);

        } catch (Exception e) {
            return createParseError();
        }
    }

    /**
     * Creates a success response.
     *
     * @param result the result data
     * @param id the request id (may be null for notifications)
     * @return JSON response string
     */
    public static String createResponse(JsonElement result, JsonElement id) {
        JsonObject response = new JsonObject();
        response.addProperty("jsonrpc", VERSION);
        
        if (result != null) {
            response.add("result", result);
        } else {
            response.add("result", com.google.gson.JsonNull.INSTANCE);
        }
        
        if (id != null) {
            response.add("id", id);
        }

        return JsonUtils.GSON.toJson(response);
    }

    /**
     * Creates an error response.
     *
     * @param code the error code
     * @param message the error message
     * @param id the request id (may be null for notifications)
     * @return JSON response string
     */
    public static String createError(int code, String message, JsonElement id) {
        JsonObject response = new JsonObject();
        response.addProperty("jsonrpc", VERSION);
        
        JsonObject error = new JsonObject();
        error.addProperty("code", code);
        error.addProperty("message", message);
        response.add("error", error);
        
        if (id != null) {
            response.add("id", id);
        }

        return JsonUtils.GSON.toJson(response);
    }

    /**
     * Creates a notification response (no id, no response expected).
     */
    public static boolean isNotification(RpcRequest request) {
        return request.id == null;
    }

    private static RpcRequest createParseError() {
        RpcRequest req = new RpcRequest("", new JsonArray(), null);
        req.errorCode = ERROR_PARSE;
        req.errorMessage = "Parse error";
        return req;
    }

    private static RpcRequest createInvalidRequest(String message) {
        RpcRequest req = new RpcRequest("", new JsonArray(), null);
        req.errorCode = ERROR_INVALID_REQUEST;
        req.errorMessage = message;
        return req;
    }

    private static RpcRequest createInvalidVersionRequest() {
        RpcRequest req = new RpcRequest("", new JsonArray(), null);
        req.errorCode = ERROR_INVALID_REQUEST;
        req.errorMessage = "Invalid JSON-RPC version";
        return req;
    }

    /**
     * Represents a parsed JSON-RPC request.
     */
    public static class RpcRequest {
        public final String method;
        public final JsonArray params;
        public final JsonElement id;
        
        // Error fields (set if request is invalid)
        public Integer errorCode = null;
        public String errorMessage = null;

        public RpcRequest(String method, JsonArray params, JsonElement id) {
            this.method = method;
            this.params = params;
            this.id = id;
        }

        public boolean hasError() {
            return errorCode != null;
        }

        public int getParamCount() {
            return params.size();
        }

        public JsonElement getParam(int index) {
            return params.get(index);
        }
    }

    /**
     * Utility class for Gson access.
     */
    public static class JsonUtils {
        public static final com.google.gson.Gson GSON = new com.google.gson.Gson();
        
        /**
         * Converts a base64 string to bytes.
         */
        public static byte[] fromBase64(String base64) {
            return java.util.Base64.getDecoder().decode(base64);
        }

        /**
         * Converts bytes to a base64 string.
         */
        public static String toBase64(byte[] bytes) {
            return java.util.Base64.getEncoder().encodeToString(bytes);
        }

        /**
         * Creates a JsonPrimitive from various types.
         */
        public static JsonElement toJson(Object value) {
            if (value == null) {
                return com.google.gson.JsonNull.INSTANCE;
            } else if (value instanceof String) {
                return new JsonPrimitive((String) value);
            } else if (value instanceof Number) {
                return new JsonPrimitive((Number) value);
            } else if (value instanceof Boolean) {
                return new JsonPrimitive((Boolean) value);
            } else {
                return GSON.toJsonTree(value);
            }
        }
    }
}
