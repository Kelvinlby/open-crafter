package mod.kelvinlby.crafter.registry;

import mod.kelvinlby.crafter.OpenCrafter;
import mod.kelvinlby.crafter.connector.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

public final class Chat {
    public static void register() {
        chat();
        OpenCrafter.LOGGER.info("Chat Registry: registered 1 command");
    }

    private static void chat() {
        CommandRegistry.register(
                CommandSpec.of("chat")
                        .description("Send a message to public chat or display it client-side only")
                        .param(ParamDef.required("message", ParamType.STRING, "The message text"))
                        .param(ParamDef.optional("send", ParamType.BOOLEAN, "Send to public server chat (default: false)"))
                        .build(),
                ctx -> {
                    MinecraftClient mc = ctx.client;
                    if (mc.player == null) {
                        throw CommandHandler.CommandException.invalidParams("Player object is null");
                    }

                    String message = ctx.getString("message");
                    boolean sendPublic = ctx.getBoolean("send", false);

                    if (sendPublic) {
                        mc.player.networkHandler.sendChatMessage(message);
                    } else {
                        Text prefix = Text.literal("[Open Crafter] ").styled(s -> s.withColor(0x8CDEDB));
                        Text body = Text.literal(message);
                        mc.inGameHud.getChatHud().addMessage(Text.empty().append(prefix).append(body));
                    }

                    return null;
                }
        );
    }
}
