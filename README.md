# open-crafter

A Fabric mod for Minecraft 26.3 that turns the player into an embodied AI agent.
An external **backend** (your model / inference engine) reads **sensors** and drives
**executors** through a low-latency local interface:

- **Control channel**: Unix domain socket (loopback TCP with a token on Windows), MessagePack messages (requests, fire-and-forget, batches, events).
- **Frame channel**: shared-memory ring (`/dev/shm/open-crafter-<pid>`), zero-copy numpy views.

Works on both OpenGL and Vulkan backends. The game's main thread never blocks on the backend.

## Sensors

| Sensor | What |
|---|---|
| Vision | `rgb`: the final presented image (HUD and menus included) · `flow`: ground-truth forward optical flow · `flow_occluded`: visibility flag. Any combination per frame. Push rate: fixed Hz (default 20 Hz), per client tick, or every frame. |
| Status | Window resolution, dimension and position, yaw/pitch, health, hunger, air bubbles, status effects (level and time) |

## Executors

| Area | What |
|---|---|
| `input.*` | Relative rotation; attack/use; hotbar; **any key mapping by id** (honors the user's bindings, hold-by-lease semantics); raw screen keys (Esc, Enter, …); unicode typing |
| `inventory.*` | Every vanilla container interaction: all click types, drag, creative, trades, beacon, recipe book, menu buttons, anvil rename, bundles |

## Quick start (backend)

```bash
pip install ./python
```

```python
from opencrafter import Client

with Client() as c:                      # finds the running game by itself
    c.vision.configure(streams=["rgb", "flow", "flow_occluded"], rate_mode="fixed_hz", fixed_hz=20)
    frames = c.frames()
    while True:
        f = frames.wait_next(timeout=1)  # f["rgb"] (H,W,4) u8, f["flow"] (H,W,2) f32, f["flow_occluded"] (H,W,1) u8
        st = c.status.read()
        c.input.hold(["forward", "sprint"])   # stream every step; released if not re-asserted
        c.input.rotate(yaw=2.0)
```

`Client()` pairs with the game automatically on Linux (also Flatpak launchers such as Prism
from Flathub), macOS and Windows: the game writes a small discovery file that the client looks
for. `python -m opencrafter` lists running games. To configure it by hand, use
the Transport tab of the settings screen in game, or `Client("unix:/path.sock")`, `Client("tcp:127.0.0.1:port",
token=...)`, `OPENCRAFTER_ENDPOINT` / `OPENCRAFTER_TOKEN` / `OPENCRAFTER_INSTANCE` in Python
([details](docs/PROTOCOL.md#11-finding-the-game-discovery)).

## Settings

`/opencrafter` (or Mod Menu's configure button) opens the settings screen, built with
[YetAnotherConfigLib](https://modrinth.com/mod/yacl), which ships inside the jar (a newer
standalone copy wins). Tabs: **Hand View**, **Vision** (push rate), **Input** (hold limits) and
**Transport** (endpoint, token). Everything is persisted in `config/open-crafter.json`.

**Hand View** (off until its *Enabled* toggle is on; purely visual) ports Singularity's Hand View
module and adds per-click swing animations:

- General: server rotations, no rotation delay, hide empty hand, skip swapping animation
- Swing Animation: *Left Click* (Vanilla / **Stub**: a short jab to 3–5 % of the swing that
  trembles while held), *Right Click* (Vanilla / **Smooth**: food slides from the hand to the
  middle and eases back when you stop, a 3D block item turns into the placed block (its place, size, shape and facing) and back while a
  flat one (torch, rail, flower) swings to 5 % and back, using an entity or block
  shakes the hand until released), old animations, swing hand, swing duration
- Main Hand / Off Hand / Arm: scale, position and rotation per axis
- Eating: disable, offset, scale, bob

It uses only chaining injectors, so it runs alongside Singularity (both hand views then stack).

## Documentation

- [Architecture](docs/ARCHITECTURE.md): layers, threading, flow semantics
- [Protocol](docs/PROTOCOL.md): wire format and shared-memory layout
- Ops: [vision](docs/ops/vision.md) · [status](docs/ops/status.md) · [input](docs/ops/input.md) · [inventory](docs/ops/inventory.md)

## Build

```bash
./gradlew build
```

Jars land in `build/libs/`. Requires Java 25.

## License

This project is licensed under the [GNU General Public License v3.0 or later](LICENSE).
