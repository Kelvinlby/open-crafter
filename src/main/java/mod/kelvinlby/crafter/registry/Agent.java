package mod.kelvinlby.crafter.registry;

import mod.kelvinlby.crafter.OpenCrafter;
import mod.kelvinlby.crafter.connector.*;

public final class Agent {

    private Agent() {}

    public static void register() {
        CommandRegistry.register(
                CommandSpec.of("agent")
                        .description("Toggle agent control. When true, all other commands execute normally; when false, they become no-ops.")
                        .param(ParamDef.required("control", ParamType.BOOLEAN,
                                "Enable (true) or disable (false) agent control over other commands."))
                        .build(),
                ctx -> {
                    boolean control = ctx.getBoolean("control");
                    CommandRegistry.setAgentControl(control);
                    OpenCrafter.LOGGER.info("Agent control set to {}", control);
                    return null;
                }
        );

        OpenCrafter.LOGGER.info("Agent Registry: registered 1 command");
    }
}
