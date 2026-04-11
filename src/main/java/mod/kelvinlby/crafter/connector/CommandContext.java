package mod.kelvinlby.crafter.connector;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.client.MinecraftClient;

import java.nio.channels.SocketChannel;

/**
 * Provides typed, named access to the validated parameters of an incoming command.
 * <p>
 * Handlers receive a {@code CommandContext} instead of a raw {@link JsonArray}.
 * Parameters are addressed by name (as declared in the {@link CommandSpec}), so
 * handlers are not coupled to argument order and read clearly at the call site.
 * </p>
 *
 * <h3>Example</h3>
 * <pre>
 * .handler(ctx -> {
 *     int x    = ctx.getInt("x");
 *     int y    = ctx.getInt("y");
 *     int z    = ctx.getInt("z");
 *     String label = ctx.getString("label", "default");
 *     // ...
 * })
 * </pre>
 *
 * <p>Typed getters throw {@link CommandHandler.CommandException} with
 * {@code ERROR_INVALID_PARAMS} when the value is absent or the wrong type,
 * so handlers never need to do their own defensive casting.</p>
 */
public final class CommandContext {

    /** The shared {@link MinecraftClient} instance, fetched once per dispatch. */
    public final MinecraftClient client;

    /** Socket channel the request arrived on — used by handlers that respond asynchronously. */
    public final SocketChannel socket;

    /** JSON-RPC request id, or {@code null} for notifications. */
    public final JsonElement requestId;

    private final String[] names;
    private final JsonElement[] values;

    /** Package-private — constructed by {@link CommandRegistry} after validation. */
    CommandContext(MinecraftClient client, SocketChannel socket, JsonElement requestId,
                   String[] names, JsonElement[] values) {
        this.client = client;
        this.socket = socket;
        this.requestId = requestId;
        this.names = names;
        this.values = values;
    }

    // -------------------------------------------------------------------------
    // Presence check
    // -------------------------------------------------------------------------

    /** Returns {@code true} if the named parameter was supplied by the caller. */
    public boolean has(String name) {
        int idx = indexOf(name);
        return idx >= 0 && values[idx] != null && !values[idx].isJsonNull();
    }

    // -------------------------------------------------------------------------
    // Typed getters (required)
    // -------------------------------------------------------------------------

    public String getString(String name) throws CommandHandler.CommandException {
        return require(name).getAsString();
    }

    public int getInt(String name) throws CommandHandler.CommandException {
        return require(name).getAsInt();
    }

    public long getLong(String name) throws CommandHandler.CommandException {
        return require(name).getAsLong();
    }

    public double getDouble(String name) throws CommandHandler.CommandException {
        return require(name).getAsDouble();
    }

    public float getFloat(String name) throws CommandHandler.CommandException {
        return require(name).getAsFloat();
    }

    public boolean getBoolean(String name) throws CommandHandler.CommandException {
        return require(name).getAsBoolean();
    }

    public JsonObject getObject(String name) throws CommandHandler.CommandException {
        return require(name).getAsJsonObject();
    }

    public JsonArray getArray(String name) throws CommandHandler.CommandException {
        return require(name).getAsJsonArray();
    }

    public JsonElement getRaw(String name) throws CommandHandler.CommandException {
        return require(name);
    }

    // -------------------------------------------------------------------------
    // Typed getters with defaults (optional params)
    // -------------------------------------------------------------------------

    public String getString(String name, String defaultValue) {
        JsonElement el = get(name);
        return (el != null) ? el.getAsString() : defaultValue;
    }

    public int getInt(String name, int defaultValue) {
        JsonElement el = get(name);
        return (el != null) ? el.getAsInt() : defaultValue;
    }

    public long getLong(String name, long defaultValue) {
        JsonElement el = get(name);
        return (el != null) ? el.getAsLong() : defaultValue;
    }

    public double getDouble(String name, double defaultValue) {
        JsonElement el = get(name);
        return (el != null) ? el.getAsDouble() : defaultValue;
    }

    public float getFloat(String name, float defaultValue) {
        JsonElement el = get(name);
        return (el != null) ? el.getAsFloat() : defaultValue;
    }

    public boolean getBoolean(String name, boolean defaultValue) {
        JsonElement el = get(name);
        return (el != null) ? el.getAsBoolean() : defaultValue;
    }

    public JsonObject getObject(String name, JsonObject defaultValue) {
        JsonElement el = get(name);
        return (el != null) ? el.getAsJsonObject() : defaultValue;
    }

    public JsonArray getArray(String name, JsonArray defaultValue) {
        JsonElement el = get(name);
        return (el != null) ? el.getAsJsonArray() : defaultValue;
    }

    public JsonElement getRaw(String name, JsonElement defaultValue) {
        JsonElement el = get(name);
        return (el != null) ? el : defaultValue;
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    private int indexOf(String name) {
        for (int i = 0; i < names.length; i++) {
            if (names[i].equals(name)) return i;
        }
        return -1;
    }

    private JsonElement get(String name) {
        int idx = indexOf(name);
        if (idx < 0) return null;
        JsonElement el = values[idx];
        return (el == null || el.isJsonNull()) ? null : el;
    }

    private JsonElement require(String name) throws CommandHandler.CommandException {
        JsonElement el = get(name);
        if (el == null) {
            throw CommandHandler.CommandException.invalidParams("Missing required param: " + name);
        }
        return el;
    }
}
