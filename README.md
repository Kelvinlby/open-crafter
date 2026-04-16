<div align="center">

<img width="636" height="78" alt="open-crafter" src="https://github.com/user-attachments/assets/f52fc767-b84b-4df0-865c-252c0d4671da" />

</br>
</br>

[![modrinth](https://img.shields.io/static/v1?label=Modrinth&message=Download&color=brightgreen&logo=modrinth&style=for-the-badge)](https://arxiv.org/)
[![release](https://img.shields.io/static/v1?label=Github&message=Release&color=white&logo=github&style=for-the-badge)](https://github.com/Kelvinlby/open-crafter/releases/latest)
[![discord](https://img.shields.io/static/v1?label=Discord&message=Chat&color=7289DA&logo=discord&style=for-the-badge)](https://discord.gg/FjRpnp3S8z)

<img width="780" height="215" alt="chat-widget" src="https://github.com/user-attachments/assets/c4c0232a-d8bb-40f9-a1f3-be0ce89e36ae" />

</div>

# User Guides

N/A

# Developer Guides
## Architecture overview

Open Crafter has three components:

- **Mod** (this repo) — Fabric client mod. Runs inside Minecraft, hosts a Unix domain socket server, and exposes game state/actions to the engine via JSON-RPC.
- **Engine** — Rust backend that drives the AI model and issues commands over the socket.
- **Web UI** — Frontend panel, opened in-game via the mod's embedded browser.

The mod and engine communicate through a Unix domain socket at `<minecraft-dir>/open-crafter/connector.socket` using newline-delimited JSON-RPC 2.0.

---

## Adding commands

### Package layout

All command registries live in:

```
src/main/java/mod/kelvinlby/crafter/registry/
```

Each file registers one logical group of commands (e.g. `GameStateRegistry`, `PlayerActionRegistry`). The relevant infrastructure classes are in `mod.kelvinlby.crafter.connector`.

### Step 1 — Create a registry class

Add a new file under `registry/`. Follow the pattern of `GameStateRegistry`:

```java
package mod.kelvinlby.crafter.registry;

public final class PlayerActionRegistry {
    private PlayerActionRegistry() {}

    public static void registerAll() {
        registerSelectHotbarSlot();
        // add more here
    }

    private static void registerSelectHotbarSlot() { ... }
}
```

Then call `PlayerActionRegistry.registerAll()` from `OpenCrafter.startSocketConnector()`.

### Step 2 — Declare the command spec

Use the `CommandSpec` builder to declare the method name, description, and typed parameters. Required parameters must come before optional ones.

```java
CommandSpec.of("select_hotbar_slot")
    .description("Selects the given hotbar slot (0–8)")
    .param(ParamDef.required("slot", ParamType.INT, "Slot index 0–8"))
    .build()
```

Available `ParamType` values: `STRING`, `INT`, `DOUBLE`, `BOOLEAN`, `OBJECT`, `ARRAY`, `ANY`.

Use `ParamDef.required(...)` for mandatory parameters and `ParamDef.optional(...)` for optional ones.

### Step 3 — Write the handler

The handler receives a `CommandContext` with typed, named getters. Use the two-argument form for optional params to supply a default.

```java
ctx -> {
    int slot = ctx.getInt("slot");
    if (slot < 0 || slot > 8) {
        throw CommandHandler.CommandException.invalidParams("Slot must be 0–8");
    }
    MinecraftClient.getInstance().player.getInventory().selectedSlot = slot;
    return null; // null = void response
}
```

Return a `JsonElement` to send data back to the caller, or `null` for void.

### Step 4 — Register

Pass the spec and handler together to `CommandRegistry.register()`:

```java
private static void registerSelectHotbarSlot() {
    CommandRegistry.register(
        CommandSpec.of("select_hotbar_slot")
            .description("Selects the given hotbar slot (0–8)")
            .param(ParamDef.required("slot", ParamType.INT, "Slot index 0–8"))
            .build(),
        ctx -> {
            int slot = ctx.getInt("slot");
            if (slot < 0 || slot > 8) {
                throw CommandHandler.CommandException.invalidParams("Slot must be 0–8");
            }
            MinecraftClient.getInstance().player.getInventory().selectedSlot = slot;
            return null;
        }
    );
}
```

### Error handling

| Situation | How to signal it |
|---|---|
| Bad parameter value | `throw CommandHandler.CommandException.invalidParams("reason")` |
| Precondition not met (e.g. not in world) | `throw CommandHandler.CommandException.invalidParams("reason")` |
| Custom error code | `throw new CommandHandler.CommandException("reason", errorCode)` |
| Unexpected failure | Let the exception propagate — it becomes a JSON-RPC internal error |

Arity and type validation (wrong number of args, wrong type) are handled automatically by the framework before your handler is called.

---

## Wire protocol reference

Commands are sent over the socket as newline-terminated JSON-RPC 2.0 messages.

Request:
```json
{"jsonrpc":"2.0","method":"select_hotbar_slot","params":[3],"id":1}
```

Success response:
```json
{"jsonrpc":"2.0","result":null,"id":1}
```

Error response:
```json
{"jsonrpc":"2.0","error":{"code":-32602,"message":"Slot must be 0–8"},"id":1}
```

Omit `"id"` to send a notification — no response will be returned.

---

## Agent control

All registered commands are gated by a global agent-control flag, which defaults to **off**. While the flag is off, any command other than `agent` still validates its parameters but executes as a no-op and returns `null`. The engine must first enable control before issuing gameplay commands:

```json
{"jsonrpc":"2.0","method":"agent","params":{"control":true},"id":1}
```

Send `{"control":false}` to disable all gameplay commands again. The `agent` command itself always runs, so control can be toggled at any time.

| Parameter | Type | Required | Description |
|---|---|---|---|
| `control` | boolean | yes | `true` enables all other commands; `false` turns them into no-ops |

