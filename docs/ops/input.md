# `input.*` ops

Executors that drive the player through Minecraft's own input layer. Every op runs on the
game main thread (`ThreadAffinity.MAIN`) and never blocks. Ops that return `null` are meant
to be sent fire-and-forget (no `"id"`), but can also be called for an error check.

Python: `opencrafter.input.Input(client)`.

| Op | Args | Result |
|---|---|---|
| `input.rotate` | `yaw`: float deg (default 0), `pitch`: float deg (default 0) | `{yaw, pitch}` after turning |
| `input.hotbar` | `slot`: int 0-8 | `{slot}` |
| `input.keys` | `down`: [key id], `lease_ms`?: int | `null` |
| `input.key` | `key`: key id, `action`: `press` (default) \| `down` \| `up`, `duration_ms`?: int | `null` |
| `input.release_all` | – | `null` |
| `input.keys.list` | – | `[{id, short, category, key, key_label, down, toggle_mode, simulated}]` |
| `input.screen_key` | `key`: physical key name, `modifiers`?: [str] | `null` |
| `input.type` | `text`: str | `{received, codepoints, screen}` |

## `input.rotate`

Relative turn in degrees: positive `yaw` turns right, positive `pitch` looks down. Pitch is
clamped to [-90, 90] exactly like vanilla. Applied through `Entity.turn`, the same method mouse
look uses: the current *and* previous-tick rotation (`yRotO`/`xRotO`) move by the same delta, so
the rendered camera (interpolated between the two) jumps to the new view in the very next frame
with no lag and no snap-back. (Setting only `yRot`/`xRot` would make the camera ease over one
tick, ~50 ms behind; setting both to absolute values is what `turn` already does.) Vehicles
get `onPassengerTurned` (e.g. boats limit the turn). Mouse sensitivity, smoothing and inversion
options are *not* applied: the degrees are exact. Unlike real mouse look, the turn also applies
while a screen is open. Error when not in a world.

## `input.hotbar`

Selects the slot like the hotbar number keys: `Inventory.setSelectedSlot` (spectators: the
spectator menu slot). The server is told by vanilla's own `ServerboundSetCarriedItemPacket` on
the next game-mode tick (or right before the next use/attack), exactly as for a key press.

## Key ids

Any `KeyMapping` id: full (`key.forward`, `key.attack`, `key.hotbar.1`, `key.examplemod.foo`)
or short without the `key.` prefix (`forward`, `attack`, `use`, `jump`, `sneak`, `sprint`,
`inventory`, `drop`, `hotbar.1`, ...). Mappings added by other mods work the same way. List
them with `input.keys.list`. Simulation goes through the mapping itself, never through a
physical key, so it works whatever the user bound (also unbound mappings). Left/right click are
simply `attack` / `use`.

Unknown ids fail the whole call (nothing is changed).

## Holds

A key is **held** while any of these sources holds it:

- **stream** – `input.keys {down: [...]}`: declarative. The listed keys are held for `lease_ms`
  (default `input.holdLeaseMillis` = 200 ms); keys held by an earlier `input.keys` that are not
  listed are released. Send it every tick/frame while the backend wants keys down; send
  `{down: []}` to release them all. If the backend stops sending (crash, disconnect), the
  lease runs out and the keys are released. Use a lease comfortably longer than your send
  interval: a gap longer than the lease is a release followed by a new press (new click).
- **explicit** – `input.key {action: down}`: held for `duration_ms` (default: the hold lease).
  A new `down` replaces the deadline. Not affected by `input.keys`.
- **press** – `input.key {action: press}`: held during exactly one client tick, then released
  at the start of the next one, so exactly one click registers.

`input.key {action: up}` ends every source for that key; `input.release_all` ends all holds.
All durations are capped by `input.maxHoldMillis` (default 60 000). Leases are checked on the
main thread every frame and every client tick, so expiry does not depend on the backend.
Everything is released when leaving a world (`ClientPlayConnectionEvents.DISCONNECT`).

### What the game sees

The held set is turned into the transitions a physical key would cause:

- **Press** (not held → held) while no screen/overlay is open: `setDown(true)` and exactly one
  click, so `isDown()` is true and `consumeClick()` returns true once. A continuous hold never
  adds clicks: holding `attack` mines, holding `use` keeps eating/drawing a bow, holding
  `forward` walks.
- **Press while a screen is open**: like a real key, it goes to the screen instead — if the
  mapping is bound to a keyboard key, that key is delivered through `input.screen_key`'s path
  (so pressing `inventory` while the inventory is open closes it). The mapping is not clicked.
- **Release** (held → not held): `setDown(false)`, unless the user is physically holding the
  bound key/button at that moment.
- **While held**: if something else puts a held non-toggle mapping up — opening/closing a screen
  (`KeyMapping.releaseAll` / `setAll`), the user releasing the same physical key — it is put
  down again (no click) as soon as no screen is open. `setAll` is followed immediately, other
  resets at the next frame/tick. So holds survive screens like a physically held key does on
  Windows/Linux, but are never applied while a screen is open (vanilla behavior).
- **Press on a key already held** (`press` op): an extra click, the key stays down (toggle
  mappings flip), and it is released one tick later unless another source still holds it.

Toggle mappings (sneak/sprint/attack/use with the "Toggle" option): only the press transition
matters, as with a physical key — a hold toggles once, release does nothing, the next press
toggles back. Their state across screens is handled by vanilla.

Notes:

- After a screen closes, vanilla blocks attacking/mining until `attack` is released once
  (`missTime`); a backend that holds `attack` through a screen must drop it for one message.
- Mining needs `attack` held. Vanilla only continues breaking a block while the mouse is
  grabbed; a simulated attack hold also mines when the window is unfocused.
- Vanilla still pauses (opens the pause menu) when the window loses focus if *Pause on Lost
  Focus* is on (F3+P). Disable it for unattended agents.
- Code that reads the physical keyboard directly (`InputConstants.isKeyDown`, e.g. some mods,
  and the F3 debug modifier combos) does not see simulated mapping holds.

## `input.screen_key`

A physical key press (down immediately followed by up) delivered through
`KeyboardHandler.keyPress`, the same entry point SDL key events use. Behavior is identical to a
real key: the open screen gets `keyPressed`/`keyReleased` (Esc closes it, Enter confirms, Tab
cycles focus, arrows/Home/End/Backspace/Delete edit text, Ctrl+A/C/V/X work); with no screen it
reaches the game (Esc opens the pause menu, F1/F2/F5... act, and any mapping bound to that key
is pressed and released).

`key`: a vanilla keyboard key name without the `key.keyboard.` prefix, `_` or `.` as separator:
`escape`, `enter`, `backspace`, `tab`, `space`, `up`, `down`, `left`, `right`, `delete`,
`insert`, `home`, `end`, `page_up`, `page_down`, `a`-`z`, `0`-`9`, `f1`-`f24`, `left.shift`,
`keypad.enter`, ... Aliases: `esc`, `return`, `del`, `ins`, `pageup`, `pagedown`. A full
`key.keyboard.*` name is accepted too. Names refer to physical key positions (US layout); the
key code in the event comes from the active OS layout, as for a real key.

`modifiers`: any of `shift`, `ctrl`, `alt`, `super` (aliases `cmd`, `meta`, `win`), and
`shortcut` (= `ctrl`, or `cmd` on macOS — use it for copy/paste/select-all).

## `input.type`

Streams text into the open screen as character events, through
`KeyboardHandler.textInput` — the path SDL text-input events take: each Unicode code point
(not UTF-16 char) becomes one `CharacterEvent`. Works for any focused text field: chat,
anvil rename, signs, books, command blocks, search boxes. Characters the field does not accept
are dropped by the field, as with real typing; for line breaks use `input.screen_key enter`.

Result: `received` is false (and nothing is sent) when no screen is open or an overlay is
shown. To type into chat, press `chat` first and wait for one client tick for the screen to open.
