package mod.kelvinlby.crafter.connector;

/**
 * Describes a single parameter in a command definition.
 * <p>
 * Use the static factory methods to build parameter descriptors:
 * </p>
 * <pre>
 * ParamDef.required("x", ParamType.INT, "X coordinate")
 * ParamDef.optional("label", ParamType.STRING, "Display label")
 * </pre>
 */
public final class ParamDef {

    public final String name;
    public final ParamType type;
    public final String description;
    public final boolean optional;

    private ParamDef(String name, ParamType type, String description, boolean optional) {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("Param name must not be blank");
        if (type == null) throw new IllegalArgumentException("Param type must not be null");
        this.name = name;
        this.type = type;
        this.description = description != null ? description : "";
        this.optional = optional;
    }

    /** Creates a required parameter. */
    public static ParamDef required(String name, ParamType type, String description) {
        return new ParamDef(name, type, description, false);
    }

    /** Creates an optional parameter. Optional params must come after all required params. */
    public static ParamDef optional(String name, ParamType type, String description) {
        return new ParamDef(name, type, description, true);
    }

    @Override
    public String toString() {
        return (optional ? "[" : "<") + name + ":" + type.typeName() + (optional ? "]" : ">");
    }
}
