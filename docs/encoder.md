# Recording encoder

Turns packet recordings (the `.mcrec` recorders) into datasets: it replays each recording in a
headless 26.3 client running open-crafter and writes what open-crafter's sensors saw, frame by
frame. It is a development tool. It is **not part of the mod**: its code lives in its own source
set (`src/encoder`) and is its own Fabric mod (`open-crafter-encoder`), loaded only by the
encoder's worker clients, and the mod jar never contains it.

## Running it

```bash
./gradlew encode -Pinput=<recording or folder> -Poutput=<datasets folder>
```

| Property | Default | |
|---|---|---|
| `-Pinput` | | a recording (`.tar` session, `.mcrec`/`.mcrec.part` file, session folder) or a folder searched recursively |
| `-Poutput` | | one episode folder per recording is written here |
| `-Pworkers` | cores / 3, at most 4 | worker clients running at once |
| `-Pconfig` | built-in defaults | capture settings, a JSON file (below) |
| `-Pprofile` | `run/` | game profile every worker copies |
| `-Pdisplay` | `offscreen` | `offscreen` (GPU, no window), `xvfb` (software rendering), `native` (visible windows) |
| `-Pretries` | 1 | extra attempts for a recording whose worker failed |
| `-PstallMinutes` | 10 | a worker with no progress for this long is killed |
| `-Pforce` | | redo recordings whose episode already exists |
| `-PreserveGb` | 20 | disk space on the output drive that is never used (see below) |

Recordings whose episode already exists are skipped, so the same command can be repeated to pick
up new recordings or retry failures. `encode-report.json` in the output folder lists what was
done, what failed and why. Worker logs are in `<output>/.encoder/logs/`.

**Disk space.** An episode folder is complete once it has `episode.json` (written last); anything
less is never left behind: a job that fails for good has its folder deleted, and a later run deletes
what a killed run left. Before a recording starts, its episode size is estimated (session length ×
a byte rate: first a high guess, then the highest rate measured on this run's finished episodes,
+15%). It starts only if that fits in the free space minus `-PreserveGb` and what the running jobs
still need; among the jobs that fit the longest goes first, so short ones fill what is left at the
end. Should the disk still drop below half the reserve, the job with the most left to write is
stopped and its folder deleted. When nothing more fits, the run stops and writes
`ENCODE-PAUSED.md` into the output folder: what is encoded, what is not, and the command to
continue, which is the same command again (it is removed by a run that leaves nothing pending).
Translated copies of recordings (`.encoder/native/`) are deleted once their episode is done.

**The profile.** Each worker's game directory is a copy of the dev client's `run/` folder: mods,
`options.txt` (GUI scale, render distance, graphics, FOV, ...), mod configs, shader packs and
resource packs. Configure everything there with `./gradlew runClient` and the encoder uses it.
`mods/`, `shaderpacks/`, `resourcepacks/` are linked, everything else is copied afresh per worker
slot at the start of every run (`<output>/.encoder/workers/`). Worlds, logs and crash reports are
left out. The worker only overrides what never shows in the picture: vsync, frame limit, pause on
lost focus and sound volume. A replay has no network, so proxy handlers that client mods put on
connections (e.g. a SOCKS proxy) are removed from the replay connection.

**Headless.** `offscreen` uses SDL's offscreen video driver: the client renders on the real GPU
(OpenGL through EGL, or Vulkan) without any window or X server. `xvfb` needs `xvfb-run` and renders
in software (slow; for machines without a GPU).

### Capture settings (`-Pconfig`)

Any key left out keeps its default.

```json
{
  "fps": 20,
  "tickPhase": 0.5,
  "width": 854, "height": 480,
  "streams": ["rgb", "flow", "flow_occluded"],
  "rgb": { "encoder": "auto", "quality": 8, "preset": "", "pixelFormat": "yuv444p10le", "file": "rgb.mkv", "extraArgs": [] },
  "flow": { "precision": "f16", "q16Scale": 16, "zstdLevel": 3 },
  "chunkSettleFrames": 30,
  "startSeconds": 0, "maxSeconds": 0,
  "s2cDelayMillis": -1,
  "serverResourcePacks": false
}
```

| Key | Meaning |
|---|---|
| `fps` | captured frames per second of recording time; ticks always run at the recorded rate |
| `tickPhase` | where each frame falls within its tick when `fps` is a multiple of 20 (0 = right after a tick) |
| `width`, `height` | framebuffer size |
| `streams` | open-crafter vision streams: `rgb`, `flow`, `flow_occluded` (see `docs/ops/vision.md`) |
| `rgb.encoder` | `auto` (default): `hevc_nvenc` where the NVIDIA GPU can run it, else `libx265`; or any ffmpeg encoder: `libsvtav1`, `av1_nvenc`, `libaom-av1`, `ffv1` (lossless), ... |
| `rgb.quality` | constant QP for NVENC, CRF otherwise (lower is better); ignored by lossless encoders |
| `rgb.preset` | empty = per encoder (NVENC `p6`, x265 `fast`, SVT-AV1 `6`) |
| `rgb.pixelFormat` | `yuv444p10le` (default); `yuv420p10le` for smaller files; `bgr0` with `ffv1` for bit-exact RGB |
| `flow.precision` | `f16` (half floats, default), `f32` (exact), `q16` (int16 fixed point, 1/`q16Scale` px) |
| `chunkSettleFrames` | while chunk meshes are still being built, render the same instant again (uncaptured) up to this often; 0 = off |
| `startSeconds`, `maxSeconds` | skip / limit captured recording time (0 = all) |
| `s2cDelayMillis` | how long server packets take to reach the replayed client; -1 = the recording's ping |
| `serverResourcePacks` | let the recorded server push resource packs (downloads them) |

**Choosing the RGB format.** Minecraft's pixel-art textures carry much of their detail in colour:
converting to 4:2:0 alone, before any compression, limits a frame to about 35.7 dB RGB PSNR, so
every 4:2:0 codec (and AV1's fast encoders, SVT-AV1 and NVENC AV1, only do 4:2:0) smears colour
edges. Measured on a real recording (854×480, 20 fps, against the bit-exact capture):

| RGB setting | Size | RGB PSNR | Cost |
|---|---|---|---|
| **HEVC 4:4:4 10-bit, NVENC, QP 8 (default)** | 2.1 MB/s | 45.8 dB | GPU, almost no CPU |
| HEVC 4:4:4 10-bit, NVENC, QP 14 | 1.1 MB/s | 40.8 dB | GPU |
| HEVC 4:4:4 10-bit, x265 CRF 8 (no NVIDIA GPU) | 2.0 MB/s | 42.6 dB | CPU |
| AV1 4:2:0 10-bit, SVT-AV1 CRF 20 | 0.63 MB/s | 33.9 dB | CPU, heavy |
| HEVC 4:4:4 lossless, NVENC | 4.6 MB/s | 51.8 dB (YUV rounding only) | GPU |
| FFV1, `bgr0` | 5.7 MB/s | bit-exact | CPU, light |

The video is full-range BT.709 and tagged so. Decode it with `Episode.rgb_frames()` or any decoder
that honours the tags: one that assumes BT.601 limited range (a common default) gets 27 dB.

Other streams: flow at `f16` about 4 MB/s (15× smaller than raw float32, half precision kept
exactly), `q16` about 1.7 MB/s (36×, 1/16 px); occlusion 0.05 MB/s; metadata 2 KB/s.

## What comes out

One folder per recording:

| File | Content |
|---|---|
| `episode.json` | what was replayed (player, versions, translated from), the capture settings, stats, problems; written last, so its presence means the episode is complete |
| `rgb.mkv` | the RGB video, one video frame per captured frame |
| `flow.ocs`, `flow_occluded.ocs` | flow and its occlusion mask, one entry per captured frame (random access) |
| `frames.jsonl.zst` | one JSON line per frame: open-crafter's frame meta (camera, ticks, `interactions`, ...), `sensors` (every non-visual sensor, e.g. `status`) and `replay` (below) |

`replay` is what the recorded player did since the previous frame:
`recording_micros`, `recorded_ticks`, `ticks_since_last_frame`, `keys` (forward, backward, left,
right, jump, sneak, sprint at the last tick), `yaw`/`pitch` (sent at the last tick), `hotbar`, and
`events` (every action packet, in order: `player_action` with action/pos/face, `use_item_on`,
`use_item`, `attack`, `interact`, `container_click`, `container_close`, `set_carried_item`, chat,
commands, ...).

Python (`opencrafter.episode`, `pip install "opencrafter[episodes]"`):

```python
from opencrafter.episode import Episode
ep = Episode("datasets/2026-08-29_10-43-24_Noth1ngLeft-...")
flow = ep.stream("flow")[42]          # (H, W, 2) float32, pixels
occluded = ep.stream("flow_occluded")[42]
for meta in ep.frames():
    meta["replay"]["keys"], meta["camera"]
for rgb in ep.rgb_frames():            # (H, W, 3) uint8, decoded with the stream's colour tags
    ...
```

To look at the flow, `python -m opencrafter.flowviz <episode> preview.mp4` writes a side-by-side
video: the RGB on the left, and on the right the same frames with the flow drawn as half-transparent
arrows on a 24 px grid, coloured by direction (`pip install "opencrafter[flowviz]"`, and ffmpeg).

### `.ocs` array streams

`"OCSTREAM1\n"`, u32 header length, header JSON (`width`, `height`, `channels`, `encoding`,
`dtype`, `scale`), then per frame u32 length + one zstd frame, then an index: u64 offset per frame,
u32 frame count, u64 index offset, `"OCSINDEX"` (all big-endian). Encodings: `f16-delta`,
`q16-delta`, `f32-delta` (each channel a plane, each element minus its left neighbour as an
integer bit pattern, then byte planes low to high) and `bits` (numpy `packbits` order).

## How a replay works

### Versions

A worker only ever decodes its own protocol (26.3). Recordings of any other version are first
translated in the encoder process with **ViaVersion / ViaBackwards** (embedded through ViaLoader),
the same code that lets real clients join servers of other versions: older recordings go forward,
newer ones backward. Two translating connections run side by side, each fed both directions so
Via's trackers see the whole conversation: the native client in front of the recorded server
(translates S2C) and the native server behind the recorded client (translates C2S). The result is
cached as `<output>/.encoder/native/<name>.mcrec`. Supporting a new version means updating the Via
versions in `gradle.properties`; recordings that already are 26.3 need nothing.

Packet ids differ between versions, so the encoder never trusts a recording's `protocolTable`; it
decodes everything with the client's own codec. What cannot be translated is counted in the logs
and `episode.json`.

### The connection

The client connects to the recording instead of a server: a real `Connection` on an in-memory
channel. Recorded S2C bytes go through the real frame decoder and packet codec, so the login,
configuration and play listeners handle them exactly as from a server. Everything the client sends
goes through `Connection.send` (where open-crafter's interaction layer sees it) and is dropped.
Packets that only make sense on a live link are filtered (compression, encryption, transfer,
disconnect, code of conduct, server resource packs unless enabled). A recording without a login
phase (the 26.3 recorder starts at configuration) gets its login made up from its header.

### The player

The recorded client's C2S packets are its input. Each of its ticks is re-enacted in the replay's
tick, in the places the real client does the same work:

- **Actions** (at the start of the tick, screens open or not): through the game mode, exactly as the
  client's own code does. Breaking starts, continues (cracks grow) and finishes when the recorded
  client finished; placing, using, eating, attacking, interacting and inventory clicks all predict
  locally and play their animations; swings follow the 26.3 client's rules. Every prediction gets
  the recorded sequence number, so the server's acknowledgements in the recording resolve it. The
  player's inventory screen opens when the recorded client clicked in it, and the pointer sits on
  the clicked slot.
- **Movement**: the player's own tick runs with the recorded keys (so sneaking, sprinting,
  swimming, item-use slowdown, walk distance and view bobbing come from vanilla code), but its
  movement is steered so the tick ends exactly at the recorded position. The camera turns smoothly
  between the rotations sent at the tick ends. While riding, the player sits where its vehicle
  puts it: a vehicle the client drives (boat, horse) is steered onto the recorded vehicle path the
  same way, any other (minecart) moves with the server's packets.
- **Packets for other mods**: the recorded motion packets are sent at the end of the tick, and
  actions send what the game mode sends, so open-crafter's interaction events, gaze, Hand View and
  contact effects react as they would live.

**Tick timing.** The recorder stamps C2S packets when the server receives them, with network
jitter: replayed as stamped, a tick's input would land 0, 1 or 2 replay ticks apart (measured:
40 % of ticks) and the player would stutter. A client ticks every 50 ms, so the encoder rebuilds
when each tick really ran from the fastest packets around it (and keeps real client stalls). On the
sample recordings 99.9 % of frames then get exactly one recorded tick.

### Time

The game clock (`Util.getNanos()`, read by the tick timer, every vanilla animation and every
open-crafter module) is replaced by a virtual clock. While the world loads it follows real time;
from the moment the recorded client reported its world loaded (`PlayerLoaded`), it is frozen and
advanced by exactly one frame period (1/`fps`) per captured frame. Frames render as fast as the GPU
allows, but ticks, partial ticks, entity interpolation, chunk fade-in, item glint, Hand View and
every other animation advance at exactly the recorded pace. With `fps` a multiple of 20 every
frame falls at the same `tickPhase` (`partial_tick` in the frame meta).

Because rendering runs ahead of real time, chunk meshes could lag behind (holes the player never
saw). When packets changed chunks or meshes are pending, the frame is first rendered uncaptured at
the same instant until the renderer (vanilla or Sodium) reports every section built, at most
`chunkSettleFrames` times.

Capture is lossless: the vision sensor waits for the GPU instead of dropping a frame (a
`FramePublisher` can ask for that), and the episode writer encodes on its own thread.

## Verified

On the three 1.21.11 sample recordings (61 minutes, 2.9 M packets): translation without a single
untranslatable packet; zero S2C/C2S decode errors in the client; every frame exactly 50 ms of game
time at the configured phase; flow aligned with the video (warping frame k by flow k reproduces
frame k−1: median error 0, the remainder at silhouettes, static clouds and alias-prone textures);
headless on the GPU at about 60-75 frames per wall second per worker at 854×480 with the default
profile (Sodium + Iris with a shader pack).

## Known limits

- Client-only state is not in a recording: which screens other than containers were open (chat,
  pause menu, F3, the recipe book), the third-person view, the mouse position. Container screens
  come from the server; the player's own inventory is opened when they clicked in it.
- The camera is the rotation the client sent: clients that spoof rotations (rotation mods) show
  the spoofed view. Mouse movement within a tick is interpolated, not recorded.
- Server packets reach the replay `s2cDelayMillis` after they were sent (default: the recorded
  ping), one value for the whole recording.
- Audio is not captured.
- Respawns and dimension changes show the loading screen for as long as this client needs, not as
  long as the recorded one did.
