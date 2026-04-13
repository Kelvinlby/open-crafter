package mod.kelvinlby.crafter.registry;

import mod.kelvinlby.crafter.OpenCrafter;
import mod.kelvinlby.crafter.connector.*;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;

public final class Input {

    private static boolean attackAsserted = false;
    private static boolean interactAsserted = false;
    private static boolean frontAsserted = false;
    private static boolean backAsserted = false;
    private static boolean leftAsserted = false;
    private static boolean rightAsserted = false;
    private static boolean sprintAsserted = false;
    private static boolean sneakAsserted = false;
    private static boolean jumpAsserted = false;

    private Input() {}

    public static void register() {
        registerAttack();
        registerInteract();
        registerSlot();
        registerMove();
        registerEsc();

        CommandRegistry.onAgentStop(Input::releaseAll);

        OpenCrafter.LOGGER.info("Input Registry: registered 5 commands");
    }

    private static void releaseAll() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null) return;
        GameOptions opts = mc.options;
        if (opts == null) return;
        attackAsserted   = setKey(mc, opts.attackKey,  false, attackAsserted);
        interactAsserted = setKey(mc, opts.useKey,     false, interactAsserted);
        frontAsserted    = setKey(mc, opts.forwardKey, false, frontAsserted);
        backAsserted     = setKey(mc, opts.backKey,    false, backAsserted);
        leftAsserted     = setKey(mc, opts.leftKey,    false, leftAsserted);
        rightAsserted    = setKey(mc, opts.rightKey,   false, rightAsserted);
        sprintAsserted   = setKey(mc, opts.sprintKey,  false, sprintAsserted);
        sneakAsserted    = setKey(mc, opts.sneakKey,   false, sneakAsserted);
        jumpAsserted     = setKey(mc, opts.jumpKey,    false, jumpAsserted);
    }

    private static boolean setKey(MinecraftClient mc, KeyBinding kb, boolean desired, boolean asserted) {
        if (desired == asserted) return asserted;
        mc.execute(() -> {
            InputUtil.Key key = KeyBindingHelper.getBoundKeyOf(kb);
            KeyBinding.setKeyPressed(key, desired);
            if (desired) KeyBinding.onKeyPressed(key);
        });
        return desired;
    }

    private static void registerAttack() {
        CommandRegistry.register(
                CommandSpec.of("attack")
                        .description("Set the left-mouse (attack) key state. true=pressed, false=released.")
                        .param(ParamDef.required("pressed", ParamType.BOOLEAN, "true=press, false=release"))
                        .build(),
                ctx -> {
                    MinecraftClient mc = ctx.client;
                    if (mc.player == null) {
                        throw CommandHandler.CommandException.invalidParams("Player object is null");
                    }
                    GameOptions opts = mc.options;
                    attackAsserted = setKey(mc, opts.attackKey, ctx.getBoolean("pressed"), attackAsserted);
                    return null;
                }
        );
    }

    private static void registerInteract() {
        CommandRegistry.register(
                CommandSpec.of("interact")
                        .description("Set the right-mouse (use) key state. true=pressed, false=released.")
                        .param(ParamDef.required("pressed", ParamType.BOOLEAN, "true=press, false=release"))
                        .build(),
                ctx -> {
                    MinecraftClient mc = ctx.client;
                    if (mc.player == null) {
                        throw CommandHandler.CommandException.invalidParams("Player object is null");
                    }
                    GameOptions opts = mc.options;
                    interactAsserted = setKey(mc, opts.useKey, ctx.getBoolean("pressed"), interactAsserted);
                    return null;
                }
        );
    }

    private static void registerSlot() {
        CommandRegistry.register(
                CommandSpec.of("slot")
                        .description("Select a hotbar slot (0-8).")
                        .param(ParamDef.required("slot", ParamType.INT, "Hotbar slot index 0-8"))
                        .build(),
                ctx -> {
                    MinecraftClient mc = ctx.client;
                    if (mc.player == null) {
                        throw CommandHandler.CommandException.invalidParams("Player object is null");
                    }
                    int slot = ctx.getInt("slot");
                    if (slot < 0 || slot > 8) {
                        throw CommandHandler.CommandException.invalidParams("slot must be 0-8, got " + slot);
                    }
                    mc.player.getInventory().setSelectedSlot(slot);
                    return null;
                }
        );
    }

    private static void registerMove() {
        CommandRegistry.register(
                CommandSpec.of("move")
                        .description("Set movement key state. All params optional; unset = released. Overwrites all 7 states each call.")
                        .param(ParamDef.optional("front",  ParamType.BOOLEAN, "Hold forward key"))
                        .param(ParamDef.optional("left",   ParamType.BOOLEAN, "Hold strafe-left key"))
                        .param(ParamDef.optional("right",  ParamType.BOOLEAN, "Hold strafe-right key"))
                        .param(ParamDef.optional("back",   ParamType.BOOLEAN, "Hold back key"))
                        .param(ParamDef.optional("sprint", ParamType.BOOLEAN, "Hold sprint key"))
                        .param(ParamDef.optional("sneak",  ParamType.BOOLEAN, "Hold sneak key"))
                        .param(ParamDef.optional("jump",   ParamType.BOOLEAN, "Hold jump key"))
                        .build(),
                ctx -> {
                    MinecraftClient mc = ctx.client;
                    if (mc.player == null) {
                        throw CommandHandler.CommandException.invalidParams("Player object is null");
                    }
                    GameOptions opts = mc.options;
                    frontAsserted  = setKey(mc, opts.forwardKey, ctx.getBoolean("front",  false), frontAsserted);
                    backAsserted   = setKey(mc, opts.backKey,    ctx.getBoolean("back",   false), backAsserted);
                    leftAsserted   = setKey(mc, opts.leftKey,    ctx.getBoolean("left",   false), leftAsserted);
                    rightAsserted  = setKey(mc, opts.rightKey,   ctx.getBoolean("right",  false), rightAsserted);
                    sprintAsserted = setKey(mc, opts.sprintKey,  ctx.getBoolean("sprint", false), sprintAsserted);
                    sneakAsserted  = setKey(mc, opts.sneakKey,   ctx.getBoolean("sneak",  false), sneakAsserted);
                    jumpAsserted   = setKey(mc, opts.jumpKey,    ctx.getBoolean("jump",   false), jumpAsserted);
                    return null;
                }
        );
    }

    private static void registerEsc() {
        CommandRegistry.register(
                CommandSpec.of("esc")
                        .description("Close any open screen until none remain.")
                        .build(),
                ctx -> {
                    MinecraftClient mc = ctx.client;
                    mc.execute(() -> {
                        while (mc.currentScreen != null) {
                            mc.currentScreen.close();
                            if (mc.currentScreen != null) {
                                mc.setScreen(null);
                            }
                        }
                    });
                    return null;
                }
        );
    }
}
