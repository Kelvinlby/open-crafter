package mod.kelvinlby.crafter.registry;

import mod.kelvinlby.crafter.OpenCrafter;
import mod.kelvinlby.crafter.connector.*;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;

public final class Input {

    private static volatile boolean attackHeld = false;
    private static volatile boolean interactHeld = false;

    private static volatile boolean attackClickPending = false;
    private static volatile boolean interactClickPending = false;

    private static volatile boolean moveFront  = false;
    private static volatile boolean moveBack   = false;
    private static volatile boolean moveLeft   = false;
    private static volatile boolean moveRight  = false;
    private static volatile boolean moveSprint = false;
    private static volatile boolean moveSneak  = false;
    private static volatile boolean moveJump   = false;

    private static boolean lastAttackAsserted = false;
    private static boolean lastInteractAsserted = false;

    private Input() {}

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(Input::onClientTick);

        registerAttack();
        registerInteract();
        registerSlot();
        registerMove();
        registerEsc();

        OpenCrafter.LOGGER.info("Input Registry: registered 5 commands");
    }

    private static void onClientTick(MinecraftClient mc) {
        GameOptions opts = mc.options;
        if (opts == null) return;

        boolean wantAttack = attackHeld || attackClickPending;
        InputUtil.Key attackKey = KeyBindingHelper.getBoundKeyOf(opts.attackKey);
        if (wantAttack) {
            KeyBinding.setKeyPressed(attackKey, true);
            KeyBinding.onKeyPressed(attackKey);
            lastAttackAsserted = true;
        } else if (lastAttackAsserted) {
            KeyBinding.setKeyPressed(attackKey, false);
            lastAttackAsserted = false;
        }
        attackClickPending = false;

        boolean wantInteract = interactHeld || interactClickPending;
        InputUtil.Key useKey = KeyBindingHelper.getBoundKeyOf(opts.useKey);
        if (wantInteract) {
            KeyBinding.setKeyPressed(useKey, true);
            KeyBinding.onKeyPressed(useKey);
            lastInteractAsserted = true;
        } else if (lastInteractAsserted) {
            KeyBinding.setKeyPressed(useKey, false);
            lastInteractAsserted = false;
        }
        interactClickPending = false;

        applyMove(opts.forwardKey, moveFront);
        applyMove(opts.backKey,    moveBack);
        applyMove(opts.leftKey,    moveLeft);
        applyMove(opts.rightKey,   moveRight);
        applyMove(opts.sprintKey,  moveSprint);
        applyMove(opts.sneakKey,   moveSneak);
        applyMove(opts.jumpKey,    moveJump);
    }

    private static void applyMove(KeyBinding kb, boolean held) {
        InputUtil.Key key = KeyBindingHelper.getBoundKeyOf(kb);
        KeyBinding.setKeyPressed(key, held);
        if (held) KeyBinding.onKeyPressed(key);
    }

    private static void registerAttack() {
        CommandRegistry.register(
                CommandSpec.of("attack")
                        .description("Simulate a left mouse click (attack / break).")
                        .param(ParamDef.optional("hold", ParamType.BOOLEAN,
                                "If true, hold until next attack call. Default: false (single click)."))
                        .build(),
                ctx -> {
                    if (ctx.client.player == null) {
                        throw CommandHandler.CommandException.invalidParams("Player object is null");
                    }
                    boolean hold = ctx.getBoolean("hold", false);
                    if (hold) {
                        attackHeld = true;
                        attackClickPending = false;
                    } else {
                        attackHeld = false;
                        attackClickPending = true;
                    }
                    return null;
                }
        );
    }

    private static void registerInteract() {
        CommandRegistry.register(
                CommandSpec.of("interact")
                        .description("Simulate a right mouse click (use / interact).")
                        .param(ParamDef.optional("hold", ParamType.BOOLEAN,
                                "If true, hold until next interact call. Default: false (single click)."))
                        .build(),
                ctx -> {
                    if (ctx.client.player == null) {
                        throw CommandHandler.CommandException.invalidParams("Player object is null");
                    }
                    boolean hold = ctx.getBoolean("hold", false);
                    if (hold) {
                        interactHeld = true;
                        interactClickPending = false;
                    } else {
                        interactHeld = false;
                        interactClickPending = true;
                    }
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
                        throw CommandHandler.CommandException.invalidParams(
                                "slot must be 0-8, got " + slot);
                    }
                    mc.player.getInventory().setSelectedSlot(slot);
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

    private static void registerMove() {
        CommandRegistry.register(
                CommandSpec.of("move")
                        .description("Set movement key state. All params optional; unset = released. Overwrites all 7 states each call; state holds until next move call.")
                        .param(ParamDef.optional("front",  ParamType.BOOLEAN, "Hold forward key"))
                        .param(ParamDef.optional("left",   ParamType.BOOLEAN, "Hold strafe-left key"))
                        .param(ParamDef.optional("right",  ParamType.BOOLEAN, "Hold strafe-right key"))
                        .param(ParamDef.optional("back",   ParamType.BOOLEAN, "Hold back key"))
                        .param(ParamDef.optional("sprint", ParamType.BOOLEAN, "Hold sprint key"))
                        .param(ParamDef.optional("sneak",  ParamType.BOOLEAN, "Hold sneak key"))
                        .param(ParamDef.optional("jump",   ParamType.BOOLEAN, "Hold jump key"))
                        .build(),
                ctx -> {
                    if (ctx.client.player == null) {
                        throw CommandHandler.CommandException.invalidParams("Player object is null");
                    }
                    moveFront  = ctx.getBoolean("front",  false);
                    moveBack   = ctx.getBoolean("back",   false);
                    moveLeft   = ctx.getBoolean("left",   false);
                    moveRight  = ctx.getBoolean("right",  false);
                    moveSprint = ctx.getBoolean("sprint", false);
                    moveSneak  = ctx.getBoolean("sneak",  false);
                    moveJump   = ctx.getBoolean("jump",   false);
                    return null;
                }
        );
    }
}
