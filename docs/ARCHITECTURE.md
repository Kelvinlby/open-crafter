# open-crafter architecture

open-crafter turns the Minecraft player into an embodied agent. An external process
(the **backend**) reads **sensors** and drives **executors**. In game it adds a settings
screen (YACL, `client.config`, opened with `/opencrafter` or from Mod Menu) and a purely
visual first-person Hand View feature.

## Layers

Lower layers never import higher ones.

| Layer | Package | May depend on |
|---|---|---|
| Contracts | `mod.kelvinlby.opencrafter.api` | JDK, `core.config` |
| Core | `mod.kelvinlby.opencrafter.core` | JDK, Gson |
| Transport | `mod.kelvinlby.opencrafter.transport` | `api`, `core` (no Minecraft classes) |
| Game integration | `mod.kelvinlby.opencrafter.client.*` | everything above + Minecraft/Fabric |
| Mixins | `mod.kelvinlby.opencrafter.mixin.<area>` | thin hooks, delegate to `client.*` |

All gameplay code is client-only (`client` entrypoint); the `main` entrypoint does nothing,
so the jar also loads on dedicated servers.

Feature areas are `ModModule`s, installed by `client.OpenCrafterClient` with a `ModContext`:

| Module | Package | Mixin config |
|---|---|---|
| Transport | `transport` | – |
| Vision (RGB + flow) | `client.vision` | `open-crafter.vision.mixins.json` |
| Status sensor | `client.status` | – |
| Interaction events | `client.interaction` | `open-crafter.interaction.mixins.json` |
| Input executors | `client.input` | `open-crafter.input.mixins.json` |
| Inventory executors | `client.inventory` | `open-crafter.inventory.mixins.json` |
| Hand View | `client.handview` (pure math in `client.handview.anim`) | `open-crafter.handview.mixins.json` |
| Gaze (render-only camera turn) | `client.gaze` (pure math in `client.gaze.anim`) | `open-crafter.gaze.mixins.json` |
| Contact effects (cracks, break shrink, hinge swing, hit push) | `client.contact` (pure math in `client.contact.anim`) | `open-crafter.contact.mixins.json` |
| Settings screen | `client.config` | – |

Settings live in `core.config` (`ConfigStore`, one POJO per area). The settings screen edits a
copy and hands it back with `ConfigStore.replace`; `onReplace` listeners apply it (the
transport reopens when its settings changed, vision takes the new rate, Hand View and input
read the live settings).

Modules register **ops** in the `HandlerRegistry` (op name → handler + `ThreadAffinity`)
and non-visual sensors in the `SensorRegistry` (sound etc. can be added later the same way).
Modules add per-frame metadata through `FrameAnnotators` (the vision sensor calls them for each
captured frame). Client modules that react to the player's in-world actions subscribe to
`client.interaction.Interactions` instead of hooking clicks themselves.
Op names are `<area>.<verb>`, e.g. `input.rotate`, `vision.configure`.

## Threading rules

- The game main thread never blocks: no socket I/O, no waiting on futures, no locks
  contended by the transport, no GPU→CPU synchronous readback.
- The transport owns its own threads. `ThreadAffinity.MAIN` handlers are queued with
  `Minecraft.execute` (drained at the start of every loop iteration, i.e. every frame)
  and run in the order they arrived on a connection.
- GPU readback is asynchronous (`CommandEncoder.copyTextureToBuffer` with a callback,
  mapped on a later frame), like vanilla `TracyFrameCapture`.

## Backend protocol

### Pairing

Each game writes a discovery file into a per-user directory that the backend also searches
(`$XDG_RUNTIME_DIR/open-crafter`; inside a Flatpak `$XDG_RUNTIME_DIR/app/<id>/open-crafter`,
the folder the sandbox shares with the host; `<temp dir>/open-crafter` on macOS and Windows).
It names the endpoint, the TCP token if any, and the frame ring. `Client()` needs no
arguments on Linux (native or Flatpak launchers), macOS and Windows. Manual overrides: the
Transport tab of the settings screen in game, `Client("<endpoint>")` or `OPENCRAFTER_*`
environment variables in Python. Details in `docs/PROTOCOL.md` §1.1–1.3.

### Control channel: Unix domain socket or loopback TCP

- Unix socket by default; TCP on `127.0.0.1` on Windows (CPython has no `AF_UNIX` there), with
  a token as the first message.
- Framing: `u32 little-endian length` + one MessagePack map.
- Request: `{"id": int, "op": str, "args": map}` → response
  `{"id": int, "ok": true, "result": any}` or `{"id": int, "ok": false, "error": str}`.
- Fire-and-forget: a request without `"id"` gets no response (use for high-rate
  streaming such as key holds / rotation). Errors are then logged only.
- `{"op": "batch", "args": {"calls": [{"op":..., "args":...}, ...]}}` runs all calls in
  order within the same main-thread drain; result is the list of per-call results.
- Events (server push): `{"event": str, "data": map}`. On connect the server sends
  `hello` with protocol version, shared-memory path and the op list.

### Frame channel: shared memory ring

A memory-mapped file (`/dev/shm/open-crafter-<pid>` on Linux, temp dir elsewhere) holding
a small header and N slots (default 3). Each slot carries a stream table (name, dtype,
channels, width, height, offset) so any combination of streams can be in one frame;
a per-slot sequence lock lets the backend read the latest frame without syscalls.
The exact byte layout is specified in `docs/PROTOCOL.md` (transport owns it).

## Vision streams

All streams share the framebuffer resolution, row-major, **top row first**, identical on
OpenGL and Vulkan, with or without shader packs.

| Stream | dtype × channels | Meaning |
|---|---|---|
| `rgb` | u8 × 4 | Final presented image (world, hand, HUD, menus), RGBA with A = 255 |
| `flow` | f32 × 2 | Forward ground-truth flow, pixels, +x right, +y down |
| `flow_occluded` | u8 × 1 | 1 = this pixel's surface is not visible in the next frame; ignore its flow |

Flow semantics: frame *k* carries `flow_{k-1→k}` defined on the **pixel grid of the
previous published frame k-1**: pixel p of frame k-1 shows surface point S; `flow[p]` is
where S projects in frame k minus p. "Published" matters: with a 20 Hz push rate the
flow spans the two pushed frames, not two consecutive rendered frames.
`flow_occluded[p] = 1` when S is hidden in frame k (behind something, mined/removed,
left the view, entity despawned). HUD/menu pixels that stay put have flow 0 and are not
occluded; GUI elements that move (carried item, items moved between slots, scrolling) get
their own motion, and what they newly cover is occluded.
Rigid world geometry and entities are exact; particles are included when feasible.

## Executors (summary)

- `input.*`: relative rotation, attack/use (left/right click), hotbar selection, any
  `KeyMapping` by id (respecting the user's binding), raw screen keys (Esc, Enter, ...),
  text typing. Holds are leases: re-asserting a key keeps it held; it is released if not
  re-asserted within `input.holdLeaseMillis`, or explicitly, and all holds are released when
  the last backend disconnects (`BackendLifecycle`).
- `inventory.*`: every container interaction the vanilla client can send.
- Each module documents its ops in `docs/ops/<module>.md` and ships Python wrappers in
  `python/opencrafter/`.
