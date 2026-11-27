# Vision ops (`vision.*`)

The vision sensor publishes images of what the player sees through the shared-memory frame
ring (see `docs/ARCHITECTURE.md`, "Vision streams"). GPU work only happens while a backend is
connected (`FramePublisher.hasReaders()`) and at least one stream is enabled.

## Streams

| Stream | dtype × ch | Content |
|---|---|---|
| `rgb` | u8 × 4 | Final presented image (world, hand, HUD, menus), RGBA, A = 255 |
| `flow` | f32 × 2 | Forward ground-truth flow in pixels, +x right, +y down |
| `flow_occluded` | u8 × 1 | 1 = the surface at this pixel is not visible in this frame |

All streams have the framebuffer size, are row-major and top row first, identical on
OpenGL and Vulkan. Any combination can be enabled. Default after start: `["rgb"]`.

Flow in frame *k* is defined on the pixel grid of the previously **published** frame
*k − 1*: `flow[p]` is where the surface seen at `p` in frame *k − 1* is in frame *k*, minus `p`.
`meta.flow_from_frame_index` names frame *k − 1*; it is `-1` (and flow is all zero) for the
first frame and after a framebuffer resize. If a frame is dropped because no shared-memory
slot was free, the next frame still refers to it: compare `flow_from_frame_index` with the
last index you received.

## Ops

### `vision.configure` (any thread)

| Arg | Type | Meaning |
|---|---|---|
| `streams` | list of str, optional | Streams to publish, e.g. `["rgb", "flow"]`; `[]` stops capture |
| `rate_mode` | str, optional | `fixed_hz` (default, 20 Hz): first rendered frame at or after each period boundary, independent of TPS; `tick`: first rendered frame after each client tick (follows TPS); `max_fps`: every rendered frame |
| `fixed_hz` | number, optional | Rate for `fixed_hz` (default 20), clamped to [0.1, 1000] |

Omitted args keep their value. `rate_mode` and `fixed_hz` are persisted
(`vision.rateMode`, `vision.fixedHz` in `config/open-crafter.json`); `streams` is not.
Returns the same map as `vision.status`.

### `vision.status` (any thread)

Returns `streams`, `rate_mode`, `fixed_hz`, `has_readers`,
`shader_pack_in_use`, `iris_loaded`, `frames_captured`, `frames_published`,
`frames_dropped_busy_gpu` (all readback buffers still in flight), `frames_dropped_no_slot`
(no free shared-memory slot), `last_frame_index`, `width`, `height`, `backend`.

### Settings screen

The **Vision** tab of the settings screen (`/opencrafter`) sets (and persists) the rate mode
and the fixed rate, and shows the status map as it was when the screen opened.

## Frame meta

`frame_index`, `capture_nanos` (`System.nanoTime()` at the start of the captured frame),
`client_tick` (client ticks since start), `game_time` (level game time), `partial_tick`,
`level_rendered`, `camera` {`x`,`y`,`z`,`yaw`,`pitch`} (as rendered, interpolated),
`width`, `height`, `backend` (`OpenGL` / `Vulkan`), `shader_pack_in_use`,
`flow_from_frame_index`, plus entries added by other modules (`interactions`, see
`docs/ops/interaction.md`).

## How it works

Hook points (all in `mixin/vision`, delegating to `client.vision.VisionHooks`):

| Hook | Purpose |
|---|---|
| `Minecraft.renderFrame` HEAD | decide whether this frame is captured (rate pacing), arm flow recording |
| after `GameRenderer.render()` in `renderFrame` | final image ready: publish finished readbacks, issue RGB/flow GPU work + async copies |
| `GameRenderer.renderLevel`: projection buffer arg, after `LevelRenderer.render` | exact world projection (incl. bobbing/nausea); snapshot world depth before the hand clears it |
| `GameRenderer.render3dHud`: hand projection, around `renderItemInHand` | hand projection; snapshot hand depth |
| `LevelRenderer.render` around `submitFeatures` | marks world submits |
| `EntityRenderDispatcher.extractEntity/submit`, `BlockEntityRenderDispatcher.submit`, `FirstPersonHandsAndItemsRenderer.submitHandsWithItems` / `submitArmWithItem` | owner of the submits (entity id, block pos, hand, each hand's arm and held item) |
| `VisionHooks.beginHandObject` (called by Hand View) | hand-pass objects of their own: each flying placed block, under its own identity |
| `VisionHooks.beginWorldObject` (called by contact effects) | world objects of their own: a temporary block model drawn in place of a block; once it coincides with the real block and vanishes, its pixels keep the static reprojection |
| `Simple/TranslucentFeatureRenderPhase.submit` | key each submit node: owner + model root/feature type + ordinal |
| `RenderTypeFeatureRenderer.prepareGroup/getVertexBuilder` | tee the vertices of keyed submits into the recorder |
| `QuadParticleGroup`, `QuadParticleRenderState.add`, `QuadParticleFeatureRenderer` | per-particle quads |
| `GuiRenderer.executeDrawRange` TAIL | replay GUI draws into the HUD mask |
| `GuiRenderer.addElementToMesh` (around `buildVertices`), `GuiRenderer.submitBlitFromItemAtlas` | record every GUI element's vertices for GUI motion; tag item icons with their item |
| Iris `HandRenderer.renderSolid` / `setupGlState` (only when Iris is installed) | shader-pack hand: depth before/after its solid hand pass, its hand projection |

**RGB.** A full-screen pass with an own pipeline samples the main render target into an
RGBA8 texture, flipping rows (both renderpearl backends store row 0 at NDC y = −1, the image
bottom) and forcing A = 1, then `copyTextureToBuffer` into one of 3 mappable buffers. The
buffer is mapped on a later frame once the copy callback ran and copied with one memcpy per
stream into the `FrameSlot`. Nothing ever waits for the GPU; if all 3 buffers are in flight
the frame is skipped (`frames_dropped_busy_gpu`).

**Flow.** On captured frames the sensor keeps the world depth, hand depth, HUD mask, camera
(projection, view rotation, position in double precision) and the view-space vertices of
moving objects. When frame *k* is captured, own GPU passes compute flow from frame *k − 1*:

1. *Static*: every pixel's reference depth is unprojected with the reference camera and
   projected with the current one (sky: as a direction, rotation only).
2. *Motion*: recorded triangles of entities, block entities, quad particles and the
   first-person hand are rasterised at their reference position (kept only where they
   match the reference depth, i.e. were visible); each fragment interpolates the triangle's
   current view-space position perspective-correctly and projects it.
3. *Occlusion*: the surface point's current position is sub-pixel, so near a depth edge
   the texel it lands in may show the other surface. The point counts as visible when any
   texel in the 3×3 neighbourhood of that texel holds the same surface (distance within
   0.05 + 1 % of the distance, in blocks), or when it lies within the depth range of its 2×2
   bilinear footprint (grazing angles). It must also not be under the current first-person
   hand, and must be on screen and in front of the camera. Objects that vanished or changed
   vertex layout are occluded. The HUD is an overlay, not scene geometry: points moving under
   it are not flagged.
4. *GUI*: every GUI element (blit, item icon, glyph, filled rectangle) is recorded with its
   vertices, UVs and alpha. Elements are matched between the frames by pipeline + texture +
   texture region + vertex count (item icons by the item's model identity instead, because the
   GUI item atlas is re-packed as items come and go); among equal ones, those that stayed put match themselves and
   the rest pair up nearest-first (picking one of three identical stacks leaves the other two
   matched to themselves and the third to the copy on the cursor). Verified in game:
   shift-click (item jumps to its new slot, the slot it lands on is occluded), pick up / put
   down (item follows the cursor), hotbar selection (+160 px for 4 slots at GUI scale 2),
   creative-tab scrolling (items move exactly 2 rows, rows scrolled out are occluded). Elements that moved are
   drawn where they were, with their per-vertex displacement and the texture's alpha test;
   elements that vanished are drawn as occluded; elements that moved or appeared are drawn where
   they are now into a cover mask.
5. *Resolve*: pixels of moved/vanished GUI elements get the element's motion (occluded if it
   vanished, left the screen or its scissor area); other HUD/menu pixels get flow 0 and are
   occluded only if a moved/new GUI element covers them now; world pixels are also occluded
   when they land under such an element. Rows are flipped and y negated.

The HUD mask replays the GUI's own draw calls (same buffers, projection, scissor) into an R8
target; a GUI fragment counts when its alpha ≥ 0.1. The vignette and nausea/portal overlays
tint the world rather than cover it and are not in the mask.

## Accuracy

| Content | Flow |
|---|---|
| Terrain, any static block geometry, sky | exact (up to float precision of the depth reprojection) |
| Entities incl. animated limbs, armor, held items, name tags, block entities (chests, signs, pistons), falling blocks, item entities | exact per triangle (vertex-to-vertex correspondence of the recorded meshes) |
| First-person hand and held item | exact (own projection) |
| Contact effects' temporary block models (a broken block shrinking, a door or lever swinging), hit entities pushed (`docs/ops/contact-effects.md`) | exact per triangle; a swing hands its pixels over to the real block when it ends |
| Hand View's placed block flying from the hand into the world | exact per triangle; it carries on the held item when that was the last one, and once on its block its pixels follow the block (the flight stays on the block until a captured frame saw it there) |
| Quad particles (smoke, flame, cloud, ...) | exact per quad, if the particle wrote depth; translucent particles without depth writes show the surface behind them |
| Mined/placed/changed blocks | old surface becomes occluded (depth no longer matches) |
| HUD, menus, chat that stay put | flow 0, occluded 0 |
| GUI elements that move: carried item following the mouse, item moved between slots (shift-click, number keys), scrolled lists, hotbar selection frame | exact per element (vertex-to-vertex) |
| Inventory View's backpack flying in and out (panel, slots, labels, player model) | exact per element; verified in game: every frame's flow equals the panel's measured shift, with and without a shader pack (Complementary r5.8.1), and the parts that leave the bottom of the window are occluded |
| GUI elements that disappear (closing a screen, an item leaving a slot) | occluded |
| What a moving or newly shown GUI element covers (the slot an item lands on, the world behind a menu that opens) | occluded |

Approximations and known limits:

- Submits are matched by owner + model root part (or feature type) + per-owner ordinal and
  must have the same vertex layout in both frames. A part whose layout changes between the
  two frames (e.g. equipment swapped) is reported occluded for that one frame; if a renderer
  reorders an object's submits of the same kind without changing their layout, that
  object's flow is wrong for that frame.
- Geometry drawn outside the feature renderers is treated as static: clouds (they drift
  slowly), rain/snow, the world border, sun/moon/stars (rotation of the sky dome over time
  is ignored), non-quad particles (item pickup, elder guardian) and other mods' direct draws.
- Entity shadows, block outlines and debug gizmos are not tracked (they never are the
  surface of a moving object).
- Animated water/lava surfaces are flat in vanilla, so static is exact; texture animation
  is not motion.
- GUI elements are matched by what they show and where; if a menu redraws an identical sprite
  elsewhere in the same frame it moved from, the nearest copy is taken. The player model and
  other picture-in-picture renders in screens are images of a separate 3D render and count as
  static. The OS mouse cursor is not part of the image.
- Screen effects drawn over the world (fire overlay, underwater/powder-snow/pumpkin
  overlays) are not masked: underneath pixels carry the world flow. The pumpkin and spyglass
  overlays are GUI draws and are masked where opaque.
- Translucent surfaces that write depth (stained glass, ice) are the visible surface; what
  is seen through them gets their flow.
- The occlusion test has a tolerance of 0.05 + 1 % of the distance; occluders thinner than
  that (rare in Minecraft) are missed. Because of the 3×3 neighbourhood, a disocclusion
  band narrower than about one pixel next to a silhouette is not flagged. Under pure camera
  rotation (no parallax) about 0.07 % of pixels in leafy scenes are still flagged: alpha-
  tested leaves rasterise differently at different sub-pixel offsets (measured in the
  `oc-e2e` world; before this test was widened it was 0.30 %, plus a line along the hotbar
  and the crosshair).

## Shader mods (Iris)

Verified in a dev client with Sodium 0.9.2 + Iris 1.11.7 (Minecraft 26.3, OpenGL):

- Iris installed, shaders off: RGB and flow work exactly as without Iris.
- With a shader pack active: RGB is the final composited image; no crash, no image corruption.
  Iris draws the first-person hand itself, inside the level pass and with its own projection;
  the sensor hooks that pass (depth before/after the solid hand, Iris' hand projection), so the
  hand and held item keep exact flow (verified with Complementary r5.8.1: arm swing tracked as
  in vanilla). Translucent held items drawn in Iris' translucent hand pass are not separated.
  Flow is otherwise **best effort**: whatever a pack moves in its vertex shaders (waving
  leaves, grass, water waves) is not known to the sensor, so those surfaces are reprojected as
  static and their moving edges come out occluded or slightly wrong; a pack may also write a
  depth that differs from the vanilla one. Turn waving foliage off in the pack's settings (or
  use no pack) where exact flow matters. `meta.shader_pack_in_use` /
  `vision.status.shader_pack_in_use` report the state.
- Vulkan backend: Iris 1.11.7 does not start there (no Iris log, `shader_pack_in_use` stays
  false with a pack selected), so shader packs only exist on OpenGL. With Iris and Sodium
  installed on Vulkan everything works as without them (verified on lavapipe, see below).
- Not verified: other real-world shader packs than Complementary. To verify: install a pack, capture `rgb,flow,flow_occluded` while turning the
  camera and walking past mobs, and compare the photometric warp error (see below) against
  the shaders-off baseline.

## Debugging and verification aids (dev only)

Headless Vulkan: set `preferredGraphicsBackend:"vulkan"` in `run/options.txt` and run with Mesa's
software driver, `VK_ICD_FILENAMES=/usr/share/vulkan/icd.d/lvp_icd.json` (plus the same in
`VK_DRIVER_FILES`), on top of the usual `xvfb-run` command; the log must say `Using graphics
backend Vulkan ... llvmpipe`.

JVM system properties (e.g. via `JAVA_TOOL_OPTIONS` for `./gradlew runClient`):

| Property | Effect |
|---|---|
| `opencrafter.vision.dump=N` | write N frames to `run/open-crafter-dump/` instead of shared memory (`*_rgb.png/.npy`, `*_flow.npy`, `*_flow_occluded.png/.npy`, `*_meta.txt`) |
| `opencrafter.vision.dumpSkip=S` | skip the first S captured frames (default 40) |
| `opencrafter.vision.streams=rgb,flow,...` | initial stream selection |
| `opencrafter.vision.debugWorld=<name>` | open or create that singleplayer world from the title screen |
| `opencrafter.vision.debugCommands=a;b` | run these commands once in the world (`+` = space) |
| `opencrafter.vision.debugCommandPeriod=N` | repeat them every N ticks |
| `opencrafter.vision.debugUsePeriod=N` | right click every N ticks (places blocks; drives Hand View's block flights) |
| `opencrafter.vision.debugSpin=deg` / `debugStrafe=blocks` | turn / move the camera every tick |

Measured with these (854×480, 20 Hz, mean absolute RGB error of frame *k − 1* vs frame *k*
sampled at `p + flow`, non-occluded pixels; "zero" = same with flow 0):

| Scene | OpenGL | Vulkan |
|---|---|---|
| Camera yaw 2°/tick | 7.4 vs 32.5 (center flow −11.97 px = f·tan 2° exactly) | – |
| Yaw + strafe, mobs | 1.6–1.8 vs 4.7–5.2; on mobs 3–8 vs 9–54 | 4.1–4.5 vs 26–30 |
| Particle cloud, static camera | particles 7–8 vs 57–59 | – |
| Hotbar slot switch (GUI scale 2), selection frame | 1.5–4.8 vs 72–92 (dx = 40 px × slot delta, dy = 0; occluded = the slot it lands on) | – |

Residual error comes from nearest-pixel sampling of sub-pixel flow on high-frequency
textures and from sprite animation.
