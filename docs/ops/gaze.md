# Gaze (`gaze`)

Mods often act on targets that are not under the crosshair: they rotate only server-side
("silent rotations"), or they act without any rotation at all. The camera then shows nothing
pointing at the target. Gaze makes the rendered first-person camera look where the body acts, so
frames show *which action, on which object, at which position*.

It is **render-only**: input, physics, the player's own rotation, everything sent to the server and
the status sensor's `rotation` are unchanged. It has no ops; it is configured in the settings
screen (Gaze tab) or `config/open-crafter.json` → `gaze`. **Off by default** (`enabled: false`):
whether training frames should show this is an open research question.

Design: [Unified Interaction Visualization](https://claude.ai/artifact/ELAZLk8XgxRRwnM8tAfCD9), phase 4.

## What the camera follows

Every frame the camera is the player's own view turned by an **offset**. The offset eases toward
the active target and back to exactly zero, where the camera is vanilla again. Being an offset, the
player's own turning always shows at once; only the extra turn is eased.

| Source | Setting | Starts when | Holds until |
|---|---|---|---|
| Server aim | `followServerAim` | at the end of a tick (after the movement packet went out) the rotation the server last received (`Interactions.sentAim`) differs from the player's own by > 0.01°; or an action off the crosshair carries a rotation > 1° off the player's own | `holdMillis` after the last spoofed tick, or after that action's outcome |
| Contact | `lookAtActions` | an action with a `contact`, `crosshair_aligned: false`, and no rotation of its own (its aim within 1° of the player's) | `holdMillis` after that action's outcome (outcomes arrive within `interaction.outcomeWindowMillis`) |

The newest target replaces the previous one at once. The turn is a critically damped spring
stepped with its exact solution: frame-rate independent, no overshoot, and a retarget keeps the
current velocity, so quick successive actions redirect the camera smoothly instead of jittering.
Yaw always turns the short way round (across ±180°).

| Setting | Default | Meaning |
|---|---|---|
| `enabled` | `false` | global toggle |
| `followServerAim` | `true` | follow a spoofed server rotation |
| `lookAtActions` | `true` | turn toward off-crosshair actions sent without rotating |
| `easeInMillis` | 150 | roughly the time to turn onto a target (~90% there) |
| `holdMillis` | 300 | time on the target after the outcome / last spoofed tick |
| `easeOutMillis` | 250 | roughly the time to turn back |

Only the local player's own first-person view turns; third person, sleeping and spectating
another entity stay vanilla (the offset resets).

## Hook and consistency

`mixin.gaze.CameraGazeMixin` injects at the end of `Camera.alignWithEntity` (called by
`Camera.update`, once per frame from `GameRenderer.update`) and calls `Camera.setRotation`. That is
after vanilla placed the camera at the eyes and before the cull frustum, the projection and
`Camera.extractRenderState` read it, so the whole frame (level, sky, particles, Sodium/Iris,
shader packs) and the `CameraRenderState` use one consistent turned camera. No backend-specific
code; the rotation goes through Minecraft's own camera, so OpenGL, Vulkan and Iris are covered alike.

- **Vision**: flow takes the camera from the render state (`viewRotationMatrix`, position), so
  the turn shows as exact camera-motion flow. Frame meta `camera.yaw/pitch` is the rendered
  camera; status `rotation` stays the player's own.
- **Crosshair**: stays at the screen centre, i.e. where the rendered camera looks.
- **First-person hand**: drawn in view space, so it follows the rendered camera naturally (the
  body turns with the gaze).
- **Block outline / hit result**: unchanged. `Minecraft.hitResult` is gameplay state (what the
  next own click hits) and is still picked from the player's own rotation; the outline stays on
  that block in the world, which is off-centre while the gaze is turned.
- **Hand View `serverRotations`**: it turns the hands from the player's view to the sent rotation.
  With Gaze following the server aim the view is already turned, so enabling both turns the hands
  twice; use one of them. (A follow-up for Hand View: measure the turn from the rendered camera,
  `gameRenderer.mainCamera().yRot()/xRot()`, instead of the player's rotation; then both compose.)

## Verified

Dev client (OpenGL, llvmpipe, 854×480, ~5–10 fps), ease/hold 1000/1200/1000 ms, frames and
`status.read` recorded per frame from Python:

- Off-crosshair actions without rotation: the camera turns onto each contact (placement ahead-left
  yaw 0 → −43°, ahead-right → +44°, break → pitch 20 → 13°) and back to exactly 0/20; the attack on
  a pig straight ahead (crosshair-aligned) leaves it still. Status `rotation` stays 0/20 throughout.
- Silent rotation (`debugSpoofRotation`): the camera settles on the spoofed aim (−45.0/29.8)
  while it is held, then turns back after `holdMillis`.
- Rapid successive actions: retargets mid-turn without jumps or jitter.
- Flow during gaze turns: centre flow matches the camera's rotation (pinhole estimate within the
  cos(pitch) factor); warp error with flow 1.4–2.7 vs 20–25 with zero flow (moving frames).
- Iris 1.11.7 + Sodium 0.9.2 with Complementary r5.8.1: same behaviour, flow error 2.7 vs 24.5.
- Not verified: the Vulkan backend (no backend-specific code is involved).

## Testing aid (dev only)

`-Dopencrafter.interaction.debugSpoofRotation=T` makes the interaction debug driver
(`debugModActions`, `docs/ops/interaction.md`) send a raw `ServerboundMovePlayerPacket.Rot`
toward each target before acting and the player's own rotation back T ticks later, like a
silent-rotation mod. Without it the driver acts without rotating (exercises `lookAtActions`).
