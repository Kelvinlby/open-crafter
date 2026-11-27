# Interaction events (`interaction`)

Every in-world action the local player sends to the server becomes one **interaction**, whoever
issued it: the keyboard, the backend's input ops, or another mod calling `MultiPlayerGameMode`
or sending the packet itself. Interactions are ground-truth action labels for frames, the way
`flow` is ground truth for motion; they also drive the mod's own visuals (Hand View).

Design: [Unified Interaction Visualization](https://claude.ai/artifact/ELAZLk8XgxRRwnM8tAfCD9).

## Capture

The source of truth is the outgoing packet (mixin on `Connection.send`, so every sender is
covered). `MultiPlayerGameMode` calls add context: the held item and the target blocks as they
were *before* the client predicted the result.

| Packet | `kind` | `target` | `contact` |
|---|---|---|---|
| `ServerboundAttackPacket` | `attack_entity` | entity | crosshair hit when it points at the entity, else where the sent aim meets the entity's box (nearest box point if it misses) |
| `ServerboundInteractPacket` | `interact_entity` | entity | the packet's location (entity-relative, converted to world space) |
| `ServerboundUseItemOnPacket` | `use_on_block` | block + face | the packet's hit location |
| `ServerboundPlayerActionPacket` START / STOP / ABORT_DESTROY_BLOCK | `break_start` / `break_done` / `break_abort` | block + face | crosshair hit when it points at the block, else the face centre |
| `ServerboundPlayerActionPacket` STAB | `stab` | none | interaction range along the aim |
| `ServerboundUseItemPacket` | `use_item` | none | none |
| `ServerboundPlayerActionPacket` RELEASE_USE_ITEM | `release_use` | none | none |

There is no serverbound swing packet in 26.3: a swing alone is not an interaction.

The **aim** is the rotation the server last received (from outgoing movement packets, so
server-side-only rotations of other mods count), or the rotation a `use_item` packet carries.

## Event `interaction`

Sent once per action, right after it was sent (for game-mode actions, after the client's own
prediction).

| Key | Type | Meaning |
|---|---|---|
| `id` | int | increasing per game session, from 1 |
| `nanos` | int | `Util.getNanos()` (the game clock) at send; comparable with frame meta `capture_nanos` |
| `frame_before` | int | index of the last frame captured before the action, `-1` if none; the action happened between that frame and the next |
| `kind` | str | see the table above |
| `hand` | `"main"` / `"off"` / nil | nil for breaking and stabbing |
| `item` | str / nil | item id held in that hand right before the action |
| `target` | map | `{"type": "none"}`, `{"type": "entity", "entity_id"}` or `{"type": "block", "pos": {x,y,z}, "face": "up"...}` |
| `contact` | map / nil | world point `{x,y,z}` where the action meets its target |
| `aim` | map | `{yaw, pitch}` in degrees, Minecraft convention |
| `crosshair_aligned` | bool | the target is what the rendered crosshair points at (no target: the aim is within 1° of the view) |
| `from_game_mode` | bool | sent through `MultiPlayerGameMode`; false for a raw packet |

## Event `interaction.outcome`

Exactly one per interaction: as soon as the world confirms a consequence, or `effect: false`
when `interaction.outcomeWindowMillis` (default 500 ms, wall clock) runs out first, or when the
world is left.

| Key | Type | Meaning |
|---|---|---|
| `id` | int | the interaction's id |
| `nanos` | int | when the outcome was decided |
| `effect` | bool | the world confirmed a consequence |
| `reason` | str | `block_changed`, `screen_opened`, `held_item_changed`, `item_in_use`, `vehicle_changed`, `entity_hurt`, `entity_removed`, or `none` |
| `block_changes` | list | `{pos, before, after}` (block ids) for `block_changed` |

What confirms which kind:

| Kind | Confirmed by |
|---|---|
| `use_on_block` | the block or the one in front of the face changed; a screen opened; the held item changed |
| `break_start`, `break_done` | the block changed (`break_start` too when the break is instant) |
| `attack_entity` | the server reported the entity hurt; the entity is gone |
| `interact_entity` | the entity is gone; a screen opened; the player mounted or dismounted; the held item changed |
| `stab` | an entity crossed by the aim ray was reported hurt |
| `use_item` | the player started using the item; a screen opened; the held item changed |
| `release_use` | an item in either hand changed |
| `break_abort` | never (immediately `effect: false`) |

## Frame meta `interactions`

Each captured frame's meta has `interactions`: the actions sent since the previous captured
frame, as `{id, kind, contact_px}`. `contact_px` is `[x, y]` in framebuffer pixels (+x right,
+y down) of `contact` projected into this frame, or nil when there is no contact point or it is
behind the camera. The projection leaves out view bobbing, so it can be a few pixels off while
walking. If frames are dropped, align by the events' `frame_before` instead.

## Verified

Headless dev clients with the debug driver below, all features on (Hand View, contact effects,
gaze): OpenGL with and without Iris + Complementary r5.8.1, and Vulkan (lavapipe) with and
without Iris/Sodium installed. Events, outcomes and `contact_px` match the actions; placements
fly, breaks shrink, the door swings, gaze turns to off-crosshair targets and back, with flow
tracking each motion.

## Testing aid (dev only)

`-Dopencrafter.interaction.debugModActions=N` runs a mod-style action every N ticks, never through
the crosshair or the keyboard. `-Dopencrafter.interaction.debugModSteps=a,b,...` picks the cycle
(default `place,attack,rawplace,break`):

| Step | Action |
|---|---|
| `place` | game-mode placement on the ground ahead-left |
| `attack` | game-mode attack on the nearest mob |
| `rawplace` | raw `ServerboundUseItemOnPacket` placement on the ground ahead-right |
| `break` | game-mode break of the block above the ground ahead-left |
| `rawbreak` | raw start-destroy there, and the raw stop once the held tool has broken it (survival mining mod) |
| `use` | game-mode use of that block (toggles a door or lever placed there) |

`-Dopencrafter.interaction.debugSpoofRotation=T` first sends a raw rotation packet toward each
step's target and the player's own rotation back T ticks later (a silent rotation, see
`docs/ops/gaze.md`). Combine with the vision debug scenario (`docs/ops/vision.md`).
