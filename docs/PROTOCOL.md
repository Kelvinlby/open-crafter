# open-crafter backend protocol (version 1)

The game exposes two channels to backend processes:

1. a **control socket** (Unix domain socket, or TCP on loopback) for requests, replies and events, and
2. a **frame ring** (memory-mapped file) carrying image-shaped sensor data.

Both are owned by `mod.kelvinlby.opencrafter.transport`. The reference client is the Python
package in `python/opencrafter/` (`client.py`, `frames.py`). Everything below is what another
client implementation needs.

All multi-byte integers in framing and in the frame ring are **little-endian**. Integers
*inside* MessagePack are big-endian, as the MessagePack spec mandates.

## 1. Control socket

### 1.1 Finding the game (discovery)

Every running game writes a **discovery file** `<instance>.json` into a per-user directory:

| Platform | Discovery directory |
|---|---|
| Linux | `$XDG_RUNTIME_DIR/open-crafter` |
| Linux, game inside a Flatpak (e.g. Prism Launcher from Flathub) | `$XDG_RUNTIME_DIR/app/<flatpak id>/open-crafter`: the one runtime folder a sandbox shares with the host, at the same path on both sides |
| macOS, Windows, Linux without `XDG_RUNTIME_DIR` | `<temp dir>/open-crafter`: `java.io.tmpdir`, i.e. the per-user `$TMPDIR` / `%TEMP%` |

The directory is mode `0700`, the file `0600` (where POSIX permissions exist).
`<instance>` is `<pid>-<6 hex digits>`, unique even across Flatpak sandboxes, which have
their own pid namespace. Content:

```json
{
  "format": 1, "protocol": 1,
  "instance": "12345-a1b2c3", "pid": 12345,
  "endpoint": "unix:/run/user/1000/open-crafter/12345-a1b2c3.sock",
  "token": null,
  "shm": "/dev/shm/open-crafter-12345-a1b2c3",
  "started_ms": 1764000000000,
  "game_dir": "/home/me/.minecraft",
  "flatpak": null
}
```

`endpoint` is `unix:<path>` or `tcp:<host>:<port>`; `token` is set only for TCP. While the
game runs it holds an exclusive lock (`fcntl` / `LockFileEx`) on `<instance>.lock` next to the
file. An entry whose lock file is missing or can be locked belongs to a game that crashed:
ignore it (games delete such entries when they start). With several live games, the
reference client picks the newest `started_ms` unless told otherwise.

### 1.2 Endpoints

| `transport.listen` | Endpoint |
|---|---|
| `auto` (default) | TCP on Windows (CPython has no Unix sockets there), a Unix socket elsewhere; falls back to TCP if the socket cannot be created |
| `unix` | `transport.socketPath`, default `<discovery dir>/<instance>.sock` |
| `tcp` | `127.0.0.1:<transport.tcpPort>`, default port 0 = any free port |

The game deletes a stale socket file when it starts and removes the file when it stops. Any
number of backends may connect at once; each connection is independent. TCP connections set
`TCP_NODELAY`.

**TCP authentication.** Loopback TCP is reachable by every local user, so the first message
on a TCP connection must be `{"op": "auth", "args": {"token": "<token>"}}` (no `id`). The
token comes from the discovery file (`transport.token`, default: a new random one per
launch). On a match the game sends `hello` and the connection behaves as below; on anything
else it closes the connection without a reply. Unix sockets need no token: the socket file is
owner-only.

### 1.3 Manual configuration

In game, in the settings screen's **Transport** tab (`/opencrafter`, or Mod Menu; persisted in
`config/open-crafter.json`, applied on save; connected backends are disconnected):

| Setting | Effect |
|---|---|
| Listen: Auto / Unix / TCP | endpoint kind |
| Socket Path (empty = default) | Unix socket file |
| TCP Port (0 = any free port) | TCP port |
| Shared Memory Path (empty = default) | frame ring file |
| Ring Slots | frames the ring holds |
| Fixed Token (empty = random per launch) | TCP token |
| Copy Token | puts the token in use on the clipboard without showing it |
| Status | endpoint, discovery file, frame ring, connections (as the screen opened) |

Backend side (reference client): `Client("unix:/path")` / `Client("tcp:127.0.0.1:25599",
token=...)`, or the environment variables `OPENCRAFTER_ENDPOINT` (`OPENCRAFTER_SOCKET`, a
socket path, also works), `OPENCRAFTER_TOKEN`, `OPENCRAFTER_INSTANCE` (choose among several
games by instance, pid or part of the game directory) and `OPENCRAFTER_DISCOVERY_DIR` (extra
directories to search first). `python -m opencrafter` lists the games it finds.

### 1.4 Framing

Each message in either direction is:

```
+---------------------------+------------------------------+
| length: u32 little-endian | body: `length` bytes         |
+---------------------------+------------------------------+
```

`body` is exactly one MessagePack value, and it must be a **map**. The maximum body length is
64 MiB (67 108 864 bytes). If a client sends a larger length, the game closes the
connection. If a body does not decode, or is not a map, the game sends an `error` event
(§1.9) and keeps the connection open.

### 1.5 MessagePack subset

The game decodes nil, bool, every int width (positive/negative fixint, uint8–64,
int8–64), float32/64, str (fixstr, str8/16/32), bin (8/16/32), array (fixarray, 16, 32) and
map (fixmap, 16, 32). Extension types are rejected. Map keys must be strings. A uint64
above 2^63 − 1 is rejected.

In Java, handlers receive these types:

| MessagePack | Java |
|---|---|
| nil | `null` |
| bool | `Boolean` |
| any integer | `Long` (always Long, whatever the width) |
| float32, float64 | `Double` |
| str | `String` |
| bin | `byte[]` |
| array | `List<Object>` |
| map | `Map<String, Object>` |

Handlers should read arguments through `api.Args`, which accepts any `Number`.

The game always encodes integers in their smallest form, `Float` as float32 and `Double` as
float64. `byte[]` and `ByteBuffer` become bin. `Collection`, `Object[]` and primitive arrays
become arrays. An enum becomes its name as a string. Map keys are encoded with
`String.valueOf`.

### 1.6 Requests and replies

Request (client → game):

```
{"id": <int>, "op": <str>, "args": <map>}
```

- `op` is required and must be a non-empty string. Op names are `<area>.<verb>`.
- `args` is optional; a missing or nil `args` is the same as `{}`.
- `id` is optional. The game echoes it back verbatim. Use unique integers per connection.

Reply (game → client), sent only when the request had a non-nil `id`:

```
{"id": <id>, "ok": true,  "result": <any>}
{"id": <id>, "ok": false, "error": <str>}
```

Errors are replies with `ok: false`. They never close the connection. The following produce
an error reply:

- an unknown op (`"Unknown op: <op>"`);
- a malformed request (missing `op`, or `args` that is not a map);
- a handler that throws. Validation exceptions (`IllegalArgumentException`,
  `IllegalStateException`, `UnsupportedOperationException`) send their plain message;
  any other exception sends `"<ExceptionType>: <message>"`;
- a handler result that cannot be encoded (`"Result could not be encoded: ..."`).

**Fire-and-forget:** a request without `id` (or with `id: nil`) never gets a reply, not even
on error. The game only logs its errors, rate-limited. Use it for high-rate streaming
commands such as key holds and rotation.

**Asynchronous handlers:** a handler may return a `CompletableFuture`/`CompletionStage`. The
reply is sent when the future completes, so replies can arrive in a different order than
the requests. Match replies by `id`.

### 1.7 Threading and ordering

Each op is registered with a thread affinity:

- `ANY` runs directly on the connection's reader thread, before the next message from that
  connection is read.
- `MAIN` is queued to the game thread with `Minecraft.execute`. The game drains that queue
  at the start of every frame.

Messages from one connection are dispatched in arrival order by a single reader thread, and
`Minecraft.execute` appends to a FIFO queue. So **MAIN ops from one connection run in the
order they were sent**, and this holds for fire-and-forget requests too. An `ANY` op that
arrives after a queued `MAIN` op may finish before it. Different connections have no
ordering relative to each other.

### 1.8 Batch

```
{"id": 1, "op": "batch", "args": {"calls": [{"op": "a.x", "args": {...}}, {"op": "b.y"}, ...]}}
```

- All calls start in list order inside **one task**. If any call is a `MAIN` op, the whole
  batch runs within one game-thread drain, so no game tick happens in between. If every
  call is an `ANY` op, the batch runs on the reader thread.
- A failing call does not stop the batch. The result is a list with one envelope per call,
  in order:
  `{"ok": true, "result": <any>}` or `{"ok": false, "error": <str>}` (unknown ops included).
- The batch reply itself is `ok: false` only when the batch is malformed: `calls` is
  missing or not a list, or an element is not a map with a string `op`.
- If a call returns a future, the next call does not wait for it. The batch reply is sent
  once every call has completed.
- Batches cannot be nested (`"Unknown op: batch"` inside a batch).
- Without `id`, the batch is fire-and-forget.

### 1.9 Events (game → client)

```
{"event": <str>, "data": <any, usually a map>}
```

Events carry no `id`. The transport itself sends these:

| event | when | data |
|---|---|---|
| `hello` | first message on every connection | `{"protocol": 1, "shm": <frame ring path or nil>, "ops": [<sorted op names>], "pid": <game pid>}` |
| `frame` | after every frame commit | `{"index": <frame index>, "slot": <slot>, "seq": <commit sequence>}` |
| `error` | a message could not be decoded | `{"message": <str>}` |

Modules publish further events through `EventSink.publish(name, payload)`. Those events go
to every open connection.

**Backpressure:** sending never blocks the game. Each connection has its own outbound queue
and writer thread. The policy is:

- Events are dropped for a connection while more than 4 MiB of output is queued for it.
  The game logs the first drop.
- `frame` events are **coalesced**: each connection keeps only the newest unsent one, so
  `seq` can skip values. Read the ring for the latest frame. Do not count events.
- Replies are never dropped. If more than 256 MiB of output piles up, the client has
  stopped reading and the game closes the connection.

## 2. Frame ring

### 2.1 Location and lifecycle

The path is announced as `hello.data.shm` (and in the discovery file). The default is
`/dev/shm/open-crafter-<instance>` on Linux outside a Flatpak, else `<discovery
dir>/<instance>.ring` (a Flatpak's `/dev/shm` is private to the sandbox; the discovery directory
is shared and, under `$XDG_RUNTIME_DIR`, also in RAM). `transport.sharedMemoryPath` overrides it.
The file has mode `0600`. It is recreated on start and deleted on shutdown. At start the game
also deletes default-named rings left behind by processes that no longer exist. Clients map the whole file **read-only**.

The file only ever grows; it never shrinks while the game runs. A mapping you made earlier
therefore stays valid for its own length.

### 2.2 File header (first 4096 bytes)

| offset | type | field | meaning |
|---|---|---|---|
| 0 | u32 | `magic` | `0x5246434F` (the bytes `"OCFR"`) |
| 4 | u32 | `version` | `1` |
| 8 | u64 | `generation` | layout generation: **odd while the layout changes**, even when stable; starts at 2 |
| 16 | u32 | `slot_count` | N slots (config `transport.ringSlots`, default 3, minimum 2) |
| 20 | u32 | `max_streams` | 16 |
| 24 | u64 | `slot_size` | bytes per slot (multiple of 4096) |
| 32 | u64 | `slots_offset` | file offset of slot 0 (multiple of 4096) |
| 40 | u64 | `latest` | `(commit_seq << 16) \| slot_index` of the newest committed frame; `0` = none yet |
| 48 | u64 | `writer_pid` | game process id |
| 56 | u32 | `slot_header_size` | 65536 |
| 60 | u32 | `meta_capacity` | 64448 (maximum meta bytes) |

Slot *i* starts at `slots_offset + i * slot_size`.

`commit_seq` is a per-run counter that starts at 1 and increases with every commit, across
layout generations too. The `frame` event's `seq` is the same number.

### 2.3 Slot layout

Offsets are relative to the start of the slot.

| offset | type | field |
|---|---|---|
| 0 | u64 | `seq`: seqlock counter, **odd while the slot is being written** |
| 8 | i64 | `frame_index` (producer-defined, e.g. a game frame counter) |
| 16 | i64 | `capture_nanos` (producer clock: the game clock `Util.getNanos()`, i.e. `System.nanoTime` unless a virtual clock drives the game) |
| 24 | u64 | `commit`: the `commit_seq` of the frame in this slot (0 = none) |
| 32 | u32 | `stream_count` (≤ 16) |
| 36 | u32 | `meta_size`: bytes of MessagePack meta (0 = no meta, read it as `{}`) |
| 64 | 16 × 64 B | stream table |
| 1088 | `meta_size` B | meta: one MessagePack map (camera, tick, …) |
| ≥ 65536 | … | stream data |

Stream table entry *k* is at `64 + 64 * k`:

| offset | type | field |
|---|---|---|
| 0 | 32 B | `name`: UTF-8, NUL-padded (≤ 31 bytes) |
| 32 | 4 B | `dtype`: ASCII numpy type string, NUL-padded: `<u1`, `<u2`, `<f2` or `<f4` |
| 36 | u32 | `channels` |
| 40 | u32 | `width` |
| 44 | u32 | `height` |
| 48 | u64 | `offset` of the data, relative to the slot start (64-byte aligned) |
| 56 | u64 | `size` in bytes = `height * width * channels * itemsize` |

The data is tightly packed and row-major, **top row first**, with channels interleaved. It
maps to a numpy array `(height, width, channels)` of the given dtype. Streams may differ in
resolution and dtype, and a frame can hold any combination of up to 16 streams. See
`ARCHITECTURE.md` for the meaning of the vision streams.

### 2.4 Writer protocol (game)

There is a single writer. The writer **never waits for readers**.

1. **Acquire.** Pick the slot with the oldest commit among slots that are neither the
   current `latest` slot nor already being written. If there is none, the frame is dropped.
   Then set `seq` to odd (store, then a store-store fence) and write the stream table, with
   `meta_size = 0`.
2. Fill in the stream data and optionally the meta. This may happen on other threads and
   over several game frames, for example during asynchronous GPU readback.
3. **Commit.** Write `frame_index`, `capture_nanos` and `commit`. Then store `seq + 1`
   (even) with *release* semantics. Then store `latest` with *release* semantics. Then send
   the `frame` event.
4. **Abort.** Set `commit = 0`, store `seq + 1` (even), and leave `latest` unchanged.

**Growth.** If a frame needs more than `slot_size` bytes (65536 plus each stream rounded up
to 64 bytes, all rounded up to 4096), the writer re-lays-out the ring:

1. Store `generation + 1` (now odd) with a full fence.
2. Extend the file. The new `slot_size` is at least 1.5 times the old one.
3. Write the new `slot_size`, `slots_offset` and `latest = 0`, and clear every slot header.
4. Store `generation + 2` (even) with release semantics.

The new slots start at offset 4096. If any slot from the old layout is still in flight,
they start at the old end of the file instead, so late writes from the old layout cannot
corrupt the new one. The game drops commits of such stale slots silently. Frames committed
before a re-layout are gone after it.

### 2.5 Reader protocol (client)

All header and seqlock words are 8-byte aligned. Read them with single aligned 8-byte
loads, and use acquire loads where your language offers them.

```
loop:
    g1 = header.generation            ; if odd → layout changing, retry shortly
    if g1 != my_generation:            ; ring was re-laid-out (or first use)
        if file is larger than my mapping: remap the whole file
        read slot_count, slot_size, slots_offset; my_generation = g1; retry
    L = header.latest                 ; 0 → no frame yet
    slot = L & 0xFFFF ; commit = L >> 16
    s1 = slot.seq                     ; odd → retry
    if slot.commit != commit: retry   ; slot already reused, re-read latest
    read frame_index, capture_nanos, stream table, meta; build views or copy the data
    (acquire fence)
    s2 = slot.seq ; g2 = header.generation
    if s1 != s2 or g2 != g1: retry    ; torn read
    → frame is consistent
```

Zero-copy views stay correct only until the writer reuses the slot, which happens after at
least `slot_count - 1` newer commits. A reader that keeps views longer than one frame
period should re-check `slot.seq == s1` and `generation == g1` when it is done with the
data (`Frame.valid()` in Python), or copy the data before the second check.

To avoid spinning, block on the socket for the next `frame` event, then read `latest` from
the ring. The event is only a wake-up hint: the ring is the source of truth.

**Python memory ordering.** The reference reader loads these words through numpy scalar
indexing, which is one aligned 8-byte load. On x86-64 (TSO) plain loads are ordered, so
the seqlock check is exact. On weakly ordered CPUs (aarch64), CPython cannot issue load
fences, so the check is best-effort; copying the data plus `valid()` keeps the race window
tiny. A native client should use acquire loads for `generation`, `latest` and `seq`, and an
acquire fence before the second `seq` read.

## 3. Python client summary

```python
from opencrafter import Client

# op names other than ops.list are illustrative
with Client() as game:                         # default socket path, background reader
    game.call("ops.list")                      # → result, raises RemoteError on ok:false
    game.send("input.rotate", yaw=1.5)         # fire-and-forget
    game.batch(("input.key", {"key": "forward"}), "status.read")   # → [results]
    game.on("chat", lambda data: print(data))  # event callback (reader thread)
    frames = game.frames()                     # FrameReader for hello.shm
    frame = frames.wait_next(timeout=1.0)      # blocks on `frame` events
    rgb = frame["rgb"]                         # (H, W, 4) uint8, zero-copy, read-only
    assert frame.valid()
```

`Client(background=False)` starts no thread. The caller reads replies inline, which avoids
a thread hop per call. Events are dispatched only while you call, `poll()` or
`wait_frame()`.
