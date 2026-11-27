# Contact effects (`contact`)

An agent's action has to be visible in RGB and optical flow as *which action, on which object,
at which position*, for every action whoever issued it, without game-only symbols. Contact
effects show the **consequence at the contact point, continuously instead of in one frame, and
only when the world really had that consequence**. They are driven by the interaction layer
(`docs/ops/interaction.md`): every action the local player sends (keyboard, the backend, another
mod through `MultiPlayerGameMode` or raw packets) and its outcome. Purely visual: the world,
collisions and the server never see them. No ops; settings in the **Contact Effects** tab of the
settings screen (`contactEffects` in `config/open-crafter.json`).

| Effect | Trigger | What is shown | Setting (default) |
|---|---|---|---|
| Break cracks | `break_start` on a block until `break_done` / `break_abort`, the block changing, or the cracks having waited `crackLingerTicks` at their last stage | the game's own crack overlay (`ClientLevel.destroyBlockProgress`, own breaker id), stage = progress × 10 with progress growing every tick by the block's destroy progress for the player and held tool, exactly as `MultiPlayerGameMode.continueDestroyBlock` counts. While the game's own break runs on that block (keyboard, or a mod driving `continueDestroyBlock`) it is left to show its cracks alone | `crackOverlay` (on), `crackLingerTicks` (20) |
| Break shrink | outcome `effect` of `break_done` or (instant) `break_start` with a block change to air or fluid | the removed block's model shrinks to its centre (smoothstep), then is gone | `breakShrink` (on), `breakShrinkTicks` (4) |
| Hinge swing | outcome `effect` of `use_on_block` where a door, trapdoor, fence gate, lever or button changed only `open` / `powered` (a gate may also turn to face the player); both halves of a door | the new state's model is drawn moving from the old pose to the new one; the real block is left out of the chunk mesh meanwhile (vanilla's and Sodium's mesher) | `hingeSwing` (on), `hingeSwingTicks` (4) |
| Hit push | outcome `entity_hurt` of `attack_entity` or `stab` | the hurt entity's whole model is drawn pushed horizontally away from the contact point, out fast (ease out, first 30 %) and smoothly back | `hitPush` (on), `hitPushTicks` (4), `hitPushBlocks` (0.06) |

Every effect starts on the first frame it is drawn, so its first frame shows the world exactly as
before however late the outcome came; it uses partial ticks, so it moves every frame. Blocks drawn
by a block-entity renderer (chests, barrels) animate themselves and are left alone. Leaving the
world or switching an effect off stops it at once.

## How the swing is found

`client.contact.anim.HingeFit` compares the quads of the old and the new state's model
(block-local): quads at the same place in both stay still; the rest are split into rigid parts
(quads sharing corners), taken from whichever model shows them apart (an open gate's two wings).
Each part gets the quarter turn about an axis parallel to x, y or z that maps it exactly onto the
other model's moving corners; of the turns that fit, the one whose axis passes closest to the
parts that stay (gate posts, lever base), else to the part itself (a door's edge), wins. A part
no turn fits but a per-axis stretch does slides instead (a button pressed in); anything else is
not animated. Turned faces are re-shaded for the direction they face (`FaceShade`), so a door
swinging round does not jump in brightness.

## Rendering and optical flow

Temporary block models are submitted inside the level's own feature submission
(`LevelRenderer.submitFeatures`, after block entities) as custom geometry with the moving-block
render types, tesselated by the game's block renderer with the light, tint and shading moving
blocks (pistons, falling blocks) get. Only Minecraft's render abstractions are used, so this works
on OpenGL and Vulkan and with shader packs.

Each temporary model is a world object of its own for the vision sensor
(`VisionHooks.beginWorldObject`), so its triangles are matched frame to frame and get exact flow.
A swing ends on the real block: the block is meshed again, the model stays drawn on top of it until
a captured frame saw both (at most 20 ticks), and then its pixels keep the static reprojection
instead of being flagged occluded. A shrinking block that vanishes is occluded (it is gone). The
hit push moves the entity's render state (`EntityRenderDispatcher.extractEntity`), so the displaced
pose is what the entity recorder records. The crack overlay is static world geometry (a decal on
the block): flow 0 relative to the block, the texture change is not motion.

## Verified (dev client, Xvfb, OpenGL, 854×480; frames from the vision dump)

Durations raised to 12 ticks (40 with a shader pack, which renders at ~2 fps) to see several
frames. "Warp error" is the mean |RGB(k−1)(p) − RGB(k)(p + flow)| over non-occluded pixels around
the effect, against the same with flow 0.

| Effect | Evidence |
|---|---|
| Break shrink (creative game-mode break) | 6 frames, radial flow towards the block centre, warp error 1.0–1.6 vs 6.6–13 with flow 0; disoccluded ground occluded |
| Hinge swing, oak door opening / closing | 5–6 frames, flow 4–37 px consistent with the turn about the hinge, warp error 0.8–4 vs 3.5–8; no jump on the first frame (warp error 1.3), handed over to the real block on the last (warp error 0.1, nothing occluded) |
| Hinge swing, fence gate | both wings turn about their posts in opposite directions, 5 frames |
| Hit push, pig hit by a game-mode attack | median flow −5 px (away, i.e. up on screen) then back +1…+2 px over 7 frames; the remaining RGB change is the game's red hurt tint |
| Break cracks, survival raw-packet break (wooden pickaxe on stone) | cracks grow over ~15 frames to the last stage and vanish with the block; flow 0, warp error < 1.6 |
| With Iris 1.11.7 + Sodium 0.9.2 + Complementary r5.8.1 | door swing tracked (flow up to 50 px, warp error 2–3.6 vs 4–4.7), handover to the real block works (nothing occluded); hit push tracked (flow 4–7 px) |

Lever and button motions are covered by unit tests (`HingeFitTest`), not frame-checked.

## Known limits

- With a shader pack the temporary model is lit by the pack's moving-block (entity) program, which
  is darker than its terrain program: the block's brightness steps once when the swing starts and
  once when it ends (mean RGB change ~6 on the door, flow 0). Without a pack the model matches the
  chunk mesh except for ambient occlusion from neighbours (mean change ≤ 4 on the first frame).
- Without Sodium the hidden block relies on vanilla's immediate section rebuild; with Sodium on its
  `LevelSlice`. Other meshers keep drawing the real block under the swinging model.
- Cracks follow the interaction layer's idea of the break; a mod that breaks with the server's
  instant-break tolerance (stopping early) shows the cracks up to where it stopped.

## Testing aid (dev only)

`-Dopencrafter.interaction.debugModSteps=a,b,...` picks the steps of the interaction debug driver
(`-Dopencrafter.interaction.debugModActions=N`): besides `place`, `attack`, `rawplace`, `break` there are `rawbreak` (raw
start, then raw stop once the held tool has broken the block ahead-left) and `use` (game-mode use of
the block ahead-left, e.g. a door set there with `debugCommands`).
