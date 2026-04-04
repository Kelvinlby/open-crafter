package mod.kelvinlby.crafter.connector;

import com.google.gson.JsonElement;
import mod.kelvinlby.crafter.OpenCrafter;
import net.minecraft.client.MinecraftClient;

import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Central registry for all JSON-RPC command definitions.
 * <p>
 * Commands are registered by pairing a {@link CommandSpec} (the parameter schema)
 * with a {@link ContextHandler} (the execution logic). The registry handles
 * validation of incoming parameters before the handler is ever called.
 * </p>
 *
 * <h3>Registering a command</h3>
 * <pre>
 * CommandRegistry.register(
 *     CommandSpec.of("move_to")
 *         .description("Teleport the player to a position")
 *         .param(ParamDef.required("x", ParamType.DOUBLE, "Target X"))
 *         .param(ParamDef.required("y", ParamType.DOUBLE, "Target Y"))
 *         .param(ParamDef.required("z", ParamType.DOUBLE, "Target Z"))
 *         .param(ParamDef.optional("relative", ParamType.BOOLEAN, "Relative coords"))
 *         .build(),
 *     ctx -> {
 *         double x = ctx.getDouble("x");
 *         double y = ctx.getDouble("y");
 *         double z = ctx.getDouble("z");
 *         boolean rel = ctx.getBoolean("relative", false);
 *         // ... teleport logic
 *         return null;
 *     }
 * );
 * </pre>
 *
 * <h3>Thread safety</h3>
 * <p>Registration and lookup are both thread-safe. Handlers may be registered
 * from any thread at any time; the internal map is a {@link ConcurrentHashMap}.</p>
 *
 * <h3>Performance</h3>
 * <p>Lookup is O(1). Validation iterates the param list once (proportional to
 * parameter count, not registry size), so scaling the command set has no impact
 * on unrelated commands.</p>
 */
public final class CommandRegistry {

    private static final ConcurrentHashMap<String, Entry> registry = new ConcurrentHashMap<>();

    private CommandRegistry() {}

    // -------------------------------------------------------------------------
    // Registration
    // -------------------------------------------------------------------------

    /**
     * Registers a command.
     *
     * @param spec    the command specification (schema + metadata)
     * @param handler the execution logic
     * @throws IllegalArgumentException if {@code spec} or {@code handler} is null
     * @throws IllegalStateException    if a command with the same method name is already registered
     */
    public static void register(CommandSpec spec, ContextHandler handler) {
        if (spec == null) throw new IllegalArgumentException("CommandSpec must not be null");
        if (handler == null) throw new IllegalArgumentException("ContextHandler must not be null");

        Entry existing = registry.putIfAbsent(spec.method, new Entry(spec, handler));
        if (existing != null) {
            throw new IllegalStateException("Command already registered: " + spec.method);
        }

        OpenCrafter.LOGGER.debug("Registered command: {} — {}", spec.usageLine(), spec.description);
    }

    /**
     * Removes a previously registered command.
     *
     * @return {@code true} if a registration existed and was removed
     */
    public static boolean unregister(String method) {
        boolean removed = registry.remove(method) != null;
        if (removed) {
            OpenCrafter.LOGGER.debug("Unregistered command: {}", method);
        }
        return removed;
    }

    /** Returns {@code true} if a command with the given method name is registered. */
    public static boolean isRegistered(String method) {
        return registry.containsKey(method);
    }

    /** Returns an unmodifiable view of all registered specs, suitable for introspection. */
    public static Collection<CommandSpec> allSpecs() {
        return Collections.unmodifiableCollection(
            registry.values().stream().map(e -> e.spec).toList()
        );
    }

    // -------------------------------------------------------------------------
    // Dispatch (called by SocketConnector)
    // -------------------------------------------------------------------------

    /**
     * Looks up the command, validates params, and invokes the handler.
     *
     * @param request the parsed JSON-RPC request
     * @return the handler's return value, or {@code null} for void responses
     * @throws CommandHandler.CommandException for method-not-found, invalid params,
     *                                         or handler-thrown errors
     * @throws Exception                       for unexpected handler failures
     */
    static JsonElement dispatch(JsonRpcProtocol.RpcRequest request) throws Exception {
        Entry entry = registry.get(request.method);
        if (entry == null) {
            throw new CommandHandler.CommandException(
                "Method not found: " + request.method,
                JsonRpcProtocol.ERROR_METHOD_NOT_FOUND
            );
        }

        // Validate params and build a typed context — throws CommandException on failure
        CommandContext ctx = entry.spec.validate(MinecraftClient.getInstance(), request.params);

        return entry.handler.handle(ctx);
    }

    // -------------------------------------------------------------------------
    // Internal
    // -------------------------------------------------------------------------

    private record Entry(CommandSpec spec, ContextHandler handler) {}
}
