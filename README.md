# open-crafter

A Fabric mod for Minecraft 26.3 that turns the player into an embodied AI agent.
An external **backend** (your model / inference engine) reads **sensors** and drives
**executors** through a low-latency local interface:

- **Control channel**: Unix domain socket, MessagePack messages (requests, fire-and-forget, batches, events).
- **Frame channel**: shared-memory ring (`/dev/shm/open-crafter-<pid>`), zero-copy numpy views.

Works on both OpenGL and Vulkan backends. The game's main thread never blocks on the backend.

## Sensors

| Sensor | What |
|---|---|
| Vision | `rgb`: the final presented image (HUD and menus included) · `flow`: ground-truth forward optical flow · `flow_occluded`: visibility flag. Any combination per frame. Push rate: per client tick (default), fixed Hz, or every frame. |
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

with Client() as c:                      # connects to $XDG_RUNTIME_DIR/open-crafter.sock
    c.vision.configure(streams=["rgb", "flow", "flow_occluded"], rate_mode="tick")
    frames = c.frames()
    while True:
        f = frames.wait_next(timeout=1)  # f["rgb"] (H,W,4) u8, f["flow"] (H,W,2) f32, f["flow_occluded"] (H,W,1) u8
        st = c.status.read()
        c.input.hold(["forward", "sprint"])   # stream every step; released if not re-asserted
        c.input.rotate(yaw=2.0)
```

Configure the push rate in game too: `/opencrafter vision rate tick|fps|hz <n>` (persisted in
`config/open-crafter.json`).

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
