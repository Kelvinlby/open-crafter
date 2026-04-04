package mod.kelvinlby.crafter.registry;

import com.google.gson.JsonObject;
import mod.kelvinlby.crafter.OpenCrafter;
import mod.kelvinlby.crafter.connector.*;
import net.minecraft.client.MinecraftClient;

public final class Rotation {
    public static void register() {
        setRotation();
        getRotation();

        OpenCrafter.LOGGER.info("Rotation Registry: registered 2 commands");
    }

    private static void setRotation() {
        CommandRegistry.register(
                CommandSpec.of("set_rotation")
                        .description("Set the player's rotation with absolute/relative yaw and pitch")
                        .param(ParamDef.required("yaw", ParamType.DOUBLE, "Yaw value"))
                        .param(ParamDef.required("pitch", ParamType.DOUBLE, "Pitch value"))
                        .param(ParamDef.optional("is_relative", ParamType.BOOLEAN, "Whether it's the delta value relative to current rotation"))
                        .build(),
                ctx -> {
                    MinecraftClient mc = ctx.client;
                    if (mc.player == null) {
                        throw CommandHandler.CommandException.invalidParams("Player object is null");
                    }

                    // Receive params by name
                    double yaw = ctx.getDouble("yaw");
                    double pitch = ctx.getDouble("pitch");
                    boolean isRelative = ctx.getBoolean("is_relative", false);

                    if (isRelative) {
                        mc.player.setYaw((float) (mc.player.getYaw() + yaw));
                        mc.player.setPitch((float) (mc.player.getPitch() + pitch));
                    } else {
                        mc.player.setYaw((float) yaw);
                        mc.player.setPitch((float) pitch);
                    }

                    // Build the return object
                    JsonObject result = new JsonObject();
                    result.addProperty("yaw", mc.player.getYaw());
                    result.addProperty("pitch", mc.player.getPitch());
                    return result;
                }
        );
    }

    private static void getRotation() {
        CommandRegistry.register(
                CommandSpec.of("get_rotation")
                        .description("Get the player's current yaw and pitch")
                        .build(),
                ctx -> {
                    MinecraftClient mc = ctx.client;
                    if (mc.player == null) {
                        throw CommandHandler.CommandException.invalidParams("Player object is null");
                    }

                    JsonObject result = new JsonObject();
                    result.addProperty("yaw", mc.player.getYaw());
                    result.addProperty("pitch", mc.player.getPitch());
                    return result;
                }
        );
    }
}