package mod.kelvinlby.crafter.connector;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * Supported parameter types for command definitions.
 * <p>
 * Each type handles validation and coercion of raw {@link JsonElement} values.
 * Type checking is intentionally lenient for numeric types (e.g. a JSON double
 * is accepted for an INT param and truncated) to accommodate clients that don't
 * distinguish integer vs floating-point in their serialiser.
 * </p>
 */
public enum ParamType {

    STRING {
        @Override
        public boolean accepts(JsonElement el) {
            return el.isJsonPrimitive() && el.getAsJsonPrimitive().isString();
        }

        @Override
        public String typeName() { return "string"; }
    },

    INT {
        @Override
        public boolean accepts(JsonElement el) {
            return el.isJsonPrimitive() && el.getAsJsonPrimitive().isNumber();
        }

        @Override
        public String typeName() { return "int"; }
    },

    DOUBLE {
        @Override
        public boolean accepts(JsonElement el) {
            return el.isJsonPrimitive() && el.getAsJsonPrimitive().isNumber();
        }

        @Override
        public String typeName() { return "double"; }
    },

    BOOLEAN {
        @Override
        public boolean accepts(JsonElement el) {
            return el.isJsonPrimitive() && el.getAsJsonPrimitive().isBoolean();
        }

        @Override
        public String typeName() { return "boolean"; }
    },

    OBJECT {
        @Override
        public boolean accepts(JsonElement el) {
            return el.isJsonObject();
        }

        @Override
        public String typeName() { return "object"; }
    },

    ARRAY {
        @Override
        public boolean accepts(JsonElement el) {
            return el.isJsonArray();
        }

        @Override
        public String typeName() { return "array"; }
    },

    /** Accepts any JSON value without type checking. */
    ANY {
        @Override
        public boolean accepts(JsonElement el) {
            return true;
        }

        @Override
        public String typeName() { return "any"; }
    };

    /**
     * Returns {@code true} if {@code el} is compatible with this type.
     */
    public abstract boolean accepts(JsonElement el);

    /**
     * Human-readable name used in error messages.
     */
    public abstract String typeName();
}
