package mod.kelvinlby.crafter.connector;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import net.minecraft.client.MinecraftClient;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Declarative specification for a single JSON-RPC command.
 * <p>
 * Build a spec with the fluent builder, then pass it to
 * {@link CommandRegistry#register(CommandSpec, ContextHandler)}:
 * </p>
 * <pre>
 * CommandSpec.of("move_to")
 *     .description("Teleport the player to coordinates")
 *     .param(ParamDef.required("x", ParamType.DOUBLE, "Target X"))
 *     .param(ParamDef.required("y", ParamType.DOUBLE, "Target Y"))
 *     .param(ParamDef.required("z", ParamType.DOUBLE, "Target Z"))
 *     .param(ParamDef.optional("relative", ParamType.BOOLEAN, "Use relative coords"))
 *     .build()
 * </pre>
 *
 * <p>The spec is immutable once built; it is safe to share across threads.</p>
 *
 * <h3>Validation rules enforced at build time:</h3>
 * <ul>
 *   <li>Method name must not be blank.</li>
 *   <li>Optional parameters must follow all required parameters.</li>
 *   <li>No duplicate parameter names.</li>
 * </ul>
 */
public final class CommandSpec {

    public final String method;
    public final String description;

    /** Immutable ordered list of parameter definitions. */
    public final List<ParamDef> params;

    /** Number of required parameters (all required come first). */
    final int requiredCount;

    /** Interned name array used by CommandContext — avoids allocation per call. */
    final String[] paramNames;

    private CommandSpec(Builder b) {
        this.method = b.method;
        this.description = b.description;
        this.params = Collections.unmodifiableList(new ArrayList<>(b.params));
        this.paramNames = params.stream().map(p -> p.name).toArray(String[]::new);

        int req = 0;
        for (ParamDef p : params) {
            if (!p.optional) req++;
        }
        this.requiredCount = req;
    }

    /** Starts building a spec for the named method. */
    public static Builder of(String method) {
        return new Builder(method);
    }

    /**
     * Validates a raw JSON params array against this spec and returns a populated
     * {@link CommandContext} ready for the handler.
     *
     * @throws CommandHandler.CommandException if the params don't satisfy the spec
     */
    CommandContext validate(MinecraftClient client, JsonArray rawParams) throws CommandHandler.CommandException {
        int given = rawParams.size();
        int total = params.size();

        if (given < requiredCount) {
            throw CommandHandler.CommandException.invalidParams(String.format(
                "'%s' requires %d argument(s), got %d. Usage: %s",
                method, requiredCount, given, usageLine()
            ));
        }
        if (given > total) {
            throw CommandHandler.CommandException.invalidParams(String.format(
                "'%s' accepts at most %d argument(s), got %d. Usage: %s",
                method, total, given, usageLine()
            ));
        }

        JsonElement[] values = new JsonElement[total];
        for (int i = 0; i < given; i++) {
            JsonElement el = rawParams.get(i);
            ParamDef def = params.get(i);
            if (!def.type.accepts(el)) {
                throw CommandHandler.CommandException.invalidParams(String.format(
                    "Param '%s' (position %d) expected %s, got: %s",
                    def.name, i, def.type.typeName(), el
                ));
            }
            values[i] = el;
        }

        return new CommandContext(client, paramNames, values);
    }

    /** Human-readable usage string, e.g. {@code move_to <x:double> <y:double> [relative:boolean]}. */
    public String usageLine() {
        StringBuilder sb = new StringBuilder(method);
        for (ParamDef p : params) {
            sb.append(' ').append(p);
        }
        return sb.toString();
    }

    // -------------------------------------------------------------------------
    // Builder
    // -------------------------------------------------------------------------

    public static final class Builder {
        private final String method;
        private String description = "";
        private final List<ParamDef> params = new ArrayList<>();

        private Builder(String method) {
            if (method == null || method.isBlank()) {
                throw new IllegalArgumentException("Command method name must not be blank");
            }
            this.method = method;
        }

        public Builder description(String description) {
            this.description = description != null ? description : "";
            return this;
        }

        public Builder param(ParamDef param) {
            if (param == null) throw new IllegalArgumentException("ParamDef must not be null");
            params.add(param);
            return this;
        }

        public CommandSpec build() {
            // Validate: optional params must follow required params
            boolean seenOptional = false;
            for (ParamDef p : params) {
                if (p.optional) {
                    seenOptional = true;
                } else if (seenOptional) {
                    throw new IllegalStateException(
                        "Required param '" + p.name + "' cannot follow an optional param in command '" + method + "'");
                }
            }

            // Validate: no duplicate names
            long distinct = params.stream().map(p -> p.name).distinct().count();
            if (distinct != params.size()) {
                throw new IllegalStateException("Duplicate parameter names in command '" + method + "'");
            }

            return new CommandSpec(this);
        }
    }
}
