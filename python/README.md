# opencrafter

Python backend client for [open-crafter](https://github.com/Kelvinlby/open-crafter), a Fabric mod
for Minecraft that exposes the game to programs: vision streams (RGB, optical flow, ...) through
shared memory, and input, inventory and status over a local socket.

```bash
pip install opencrafter                # client for a running game
pip install "opencrafter[episodes]"    # + reading datasets written by the recording encoder
pip install "opencrafter[flowviz]"     # + side-by-side RGB / flow-arrow preview videos
```

```python
from opencrafter import Client

with Client() as c:                      # finds the running game by itself
    c.vision.configure(streams=["rgb", "flow"], rate_mode="fixed_hz", fixed_hz=20)
    frames = c.frames()
    f = frames.wait_next(timeout=1)      # f["rgb"] (H,W,4) u8, f["flow"] (H,W,2) f32
    c.input.hold(["forward", "sprint"])
```

`python -m opencrafter` lists running games. Encoded episodes are read with
`opencrafter.episode.Episode`; `python -m opencrafter.flowviz <episode> preview.mp4` renders a
preview. See the [repository](https://github.com/Kelvinlby/open-crafter) for the protocol and the
encoder documentation.
