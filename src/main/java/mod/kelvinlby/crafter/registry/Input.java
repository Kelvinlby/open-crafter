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

    private static boolean lastMoveFrontAsserted  = false;
    private static boolean lastMoveBackAsserted   = false;
    private static boolean lastMoveLeftAsserted   = false;
    private static boolean lastMoveRightAsserted  = false;
    private static boolean lastMoveSprintAsserted = false;
    private static boolean lastMoveSneakAsserted  = false;
    private static boolean lastMoveJumpAsserted   = false;

    private static boolean lastAgentControl = false;

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

        boolean agentOn = CommandRegistry.isAgentControl();
        if (!agentOn) {
            if (lastAgentControl) {
                releaseAll(opts);
                lastAgentControl = false;
            }
            return;
        }
        lastAgentControl = true;

        lastAttackAsserted = applyClickable(
                opts.attackKey, attackHeld, attackClickPending, lastAttackAsserted);
        attackClickPending = false;

        lastInteractAsserted = applyClickable(
                opts.useKey, interactHeld, interactClickPending, lastInteractAsserted);
        interactClickPending = false;

        lastMoveFrontAsserted  = applyMove(opts.forwardKey, moveFront,  lastMoveFrontAsserted);
        lastMoveBackAsserted   = applyMove(opts.backKey,    moveBack,   lastMoveBackAsserted);
        lastMoveLeftAsserted   = applyMove(opts.leftKey,    moveLeft,   lastMoveLeftAsserted);
        lastMoveRightAsserted  = applyMove(opts.rightKey,   moveRight,  lastMoveRightAsserted);
        lastMoveSprintAsserted = applyMove(opts.sprintKey,  moveSprint, lastMoveSprintAsserted);
        lastMoveSneakAsserted  = applyMove(opts.sneakKey,   moveSneak,  lastMoveSneakAsserted);
        lastMoveJumpAsserted   = applyMove(opts.jumpKey,    moveJump,   lastMoveJumpAsserted);
    }

    private static void releaseAll(GameOptions opts) {
        attackHeld = false;
        interactHeld = false;
        moveFront = false;
        moveBack = false;
        moveLeft = false;
        moveRight = false;
        moveSprint = false;
        moveSneak = false;
        moveJump = false;

        if (lastAttackAsserted) {
            KeyBinding.setKeyPressed(KeyBindingHelper.getBoundKeyOf(opts.attackKey), false);
        }
        if (lastInteractAsserted) {
            KeyBinding.setKeyPressed(KeyBindingHelper.getBoundKeyOf(opts.useKey), false);
        }
        lastMoveFrontAsserted  = releaseMoveIfAsserted(opts.forwardKey, lastMoveFrontAsserted);
        lastMoveBackAsserted   = releaseMoveIfAsserted(opts.backKey,    lastMoveBackAsserted);
        lastMoveLeftAsserted   = releaseMoveIfAsserted(opts.leftKey,    lastMoveLeftAsserted);
        lastMoveRightAsserted  = releaseMoveIfAsserted(opts.rightKey,   lastMoveRightAsserted);
        lastMoveSprintAsserted = releaseMoveIfAsserted(opts.sprintKey,  lastMoveSprintAsserted);
        lastMoveSneakAsserted  = releaseMoveIfAsserted(opts.sneakKey,   lastMoveSneakAsserted);
        lastMoveJumpAsserted   = releaseMoveIfAsserted(opts.jumpKey,    lastMoveJumpAsserted);

        lastAttackAsserted = false;
        lastInteractAsserted = false;
    }

    private static boolean applyClickable(KeyBinding kb, boolean held, boolean clickPending, boolean wasAsserted) {
        InputUtil.Key key = KeyBindingHelper.getBoundKeyOf(kb);
        if (held || clickPending) {
            KeyBinding.setKeyPressed(key, true);
            if (!wasAsserted) {
                KeyBinding.onKeyPressed(key);
            }
            return true;
        }
        if (wasAsserted) {
            KeyBinding.setKeyPressed(key, false);
        }
        return false;
    }

    private static boolean applyMove(KeyBinding kb, boolean held, boolean wasAsserted) {
        if (held) {
            InputUtil.Key key = KeyBindingHelper.getBoundKeyOf(kb);
            KeyBinding.setKeyPressed(key, true);
            if (!wasAsserted) {
                KeyBinding.onKeyPressed(key);
            }
            return true;
        }
        if (wasAsserted) {
            KeyBinding.setKeyPressed(KeyBindingHelper.getBoundKeyOf(kb), false);
        }
        return false;
    }

    private static boolean releaseMoveIfAsserted(KeyBinding kb, boolean wasAsserted) {
        if (wasAsserted) {
            KeyBinding.setKeyPressed(KeyBindingHelper.getBoundKeyOf(kb), false);
        }
        return false;
    }

    private static void registerAttack() {
        CommandRegistry.register(
                CommandSpec.of("attack")
                        .description("Simulate a left mouse action. Omit 'hold' for a single click (key pressed for one tick). Pass hold=true/false to toggle a continuous press; repeating the same hold state is a no-op.")
                        .param(ParamDef.optional("hold", ParamType.BOOLEAN,
                                "If present: true=start holding, false=stop holding. If absent: single click."))
                        .build(),
                ctx -> {
                    if (ctx.client.player == null) {
                        throw CommandHandler.CommandException.invalidParams("Player object is null");
                    }
                    if (ctx.has("hold")) {
                        attackHeld = ctx.getBoolean("hold");
                    } else {
                        attackClickPending = true;
                    }
                    return null;
                }
        );
    }

    private static void registerInteract() {
        CommandRegistry.register(
                CommandSpec.of("interact")
                        .description("Simulate a right mouse action. Omit 'hold' for a single click (key pressed for one tick). Pass hold=true/false to toggle a continuous press; repeating the same hold state is a no-op.")
                        .param(ParamDef.optional("hold", ParamType.BOOLEAN,
                                "If present: true=start holding, false=stop holding. If absent: single click."))
                        .build(),
                ctx -> {
                    if (ctx.client.player == null) {
                        throw CommandHandler.CommandException.invalidParams("Player object is null");
                    }
                    if (ctx.has("hold")) {
                        interactHeld = ctx.getBoolean("hold");
                    } else {
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
