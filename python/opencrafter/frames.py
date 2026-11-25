"""Shared-memory frame ring reader (see docs/PROTOCOL.md, "Frame ring").

Frames are read without syscalls: the reader maps the ring file read-only and uses the
per-slot sequence lock. Streams come back as numpy arrays shaped ``(height, width,
channels)``, row-major, top row first.

Zero-copy views (``copy=False``, the default) point straight into shared memory. They are
consistent when returned, but the game reuses a slot after ``slot_count - 1`` newer
commits; call ``frame.valid()`` after you are done with the data (or pass ``copy=True``) to
be sure it was not overwritten meanwhile.

Memory ordering: header and seqlock words are 8-byte aligned and read with single numpy
scalar loads. The game writes with release stores; on x86-64 (TSO) plain loads are enough.
On weakly ordered CPUs (aarch64) CPython offers no load fences, so the seqlock re-check is
best-effort there; ``copy=True`` plus ``valid()`` keeps the window small.
"""
from __future__ import annotations

import mmap
import os
import time
from dataclasses import dataclass
from typing import TYPE_CHECKING, Any

import msgpack
import numpy as np

if TYPE_CHECKING:
    from .client import Client

__all__ = ["Frame", "FrameReader", "RingFormatError", "StreamInfo"]

MAGIC = 0x5246434F  # b"OCFR" little-endian
VERSION = 1

H_MAGIC = 0
H_VERSION = 4
H_GENERATION = 8
H_SLOT_COUNT = 16
H_MAX_STREAMS = 20
H_SLOT_SIZE = 24
H_SLOTS_OFFSET = 32
H_LATEST = 40
H_WRITER_PID = 48
H_SLOT_HEADER_SIZE = 56
H_META_CAPACITY = 60

S_SEQ = 0
S_FRAME_INDEX = 8
S_CAPTURE_NANOS = 16
S_COMMIT = 24
S_STREAM_COUNT = 32
S_META_SIZE = 36
S_STREAM_TABLE = 64
S_META = S_STREAM_TABLE + 16 * 64

STREAM_ENTRY_SIZE = 64
E_NAME = 0
NAME_BYTES = 32
E_DTYPE = 32
DTYPE_BYTES = 4
E_CHANNELS = 36
E_WIDTH = 40
E_HEIGHT = 44
E_OFFSET = 48
E_SIZE = 56

LATEST_SLOT_BITS = 16
LATEST_SLOT_MASK = (1 << LATEST_SLOT_BITS) - 1

_RETRY = object()


class RingFormatError(RuntimeError):
    """The file is not an open-crafter frame ring of a supported version."""


@dataclass(frozen=True)
class StreamInfo:
    name: str
    dtype: np.dtype
    channels: int
    width: int
    height: int
    offset: int
    size: int

    @property
    def shape(self) -> tuple[int, int, int]:
        return self.height, self.width, self.channels


class Frame:
    """One committed frame: ``frame["rgb"]`` is the rgb stream as an ndarray."""

    __slots__ = ("index", "capture_nanos", "seq", "slot", "streams", "infos", "meta", "copied",
                 "_reader", "_generation", "_slot_seq")

    def __init__(self, reader: "FrameReader", generation: int, slot_seq: int, *, index: int,
                 capture_nanos: int, seq: int, slot: int, streams: dict[str, np.ndarray],
                 infos: dict[str, StreamInfo], meta: dict, copied: bool):
        self.index = index
        self.capture_nanos = capture_nanos
        self.seq = seq
        self.slot = slot
        self.streams = streams
        self.infos = infos
        self.meta = meta
        self.copied = copied
        self._reader = reader
        self._generation = generation
        self._slot_seq = slot_seq

    def __getitem__(self, name: str) -> np.ndarray:
        return self.streams[name]

    def __contains__(self, name: str) -> bool:
        return name in self.streams

    def valid(self) -> bool:
        """True while the slot still holds this frame (always true for copied frames)."""
        return self.copied or self._reader._unchanged(self._generation, self.slot, self._slot_seq)

    def copy(self) -> "Frame":
        """Detaches the arrays from shared memory (check ``valid()`` afterwards for views)."""
        return Frame(self._reader, self._generation, self._slot_seq, index=self.index,
                     capture_nanos=self.capture_nanos, seq=self.seq, slot=self.slot,
                     streams={name: array.copy() for name, array in self.streams.items()},
                     infos=self.infos, meta=self.meta, copied=True)

    def __repr__(self) -> str:
        shapes = {name: array.shape for name, array in self.streams.items()}
        return f"Frame(index={self.index}, seq={self.seq}, streams={shapes})"


class _Mapping:
    """Read-only map of the ring file at one layout generation."""

    def __init__(self, path: str):
        fd = os.open(path, os.O_RDONLY)
        try:
            size = os.fstat(fd).st_size
            self.mm = mmap.mmap(fd, size, access=mmap.ACCESS_READ)
        finally:
            os.close(fd)
        self.size = size
        self.u8 = np.frombuffer(self.mm, dtype=np.uint8)
        self.u32 = np.frombuffer(self.mm, dtype="<u4", count=size // 4)
        self.u64 = np.frombuffer(self.mm, dtype="<u8", count=size // 8)
        self.generation = 0
        self.slot_count = 0
        self.slot_size = 0
        self.slots_offset = 0

    def q(self, offset: int) -> int:
        return int(self.u64[offset >> 3])

    def d(self, offset: int) -> int:
        return int(self.u32[offset >> 2])

    def text(self, offset: int, width: int) -> str:
        raw = self.mm[offset:offset + width]
        return raw.split(b"\0", 1)[0].decode("utf-8")

    def check_identity(self) -> None:
        if self.size < 4096 or self.d(H_MAGIC) != MAGIC:
            raise RingFormatError("not an open-crafter frame ring (bad magic)")
        if self.d(H_VERSION) != VERSION:
            raise RingFormatError(f"unsupported ring version {self.d(H_VERSION)}")

    def load_layout(self) -> bool:
        """Reads the layout of a stable generation; False if it changes or does not fit."""
        generation = self.q(H_GENERATION)
        if generation & 1:
            return False
        self.slot_count = self.d(H_SLOT_COUNT)
        self.slot_size = self.q(H_SLOT_SIZE)
        self.slots_offset = self.q(H_SLOTS_OFFSET)
        self.generation = generation
        fits = self.slots_offset + self.slot_count * self.slot_size <= self.size
        return fits and self.q(H_GENERATION) == generation

    def slot_base(self, slot: int) -> int:
        return self.slots_offset + slot * self.slot_size


class FrameReader:
    """Reads the newest frame of the ring at ``path``.

    With a ``client`` (see ``Client.frames()``), ``wait_next`` blocks on the socket's
    ``frame`` events instead of polling shared memory.
    """

    def __init__(self, path: str, client: "Client | None" = None, *,
                 poll_interval: float = 0.0005, max_retries: int = 10000):
        self.path = path
        self._client = client
        self._poll_interval = poll_interval
        self._max_retries = max_retries
        self._map = _Mapping(path)
        self._map.check_identity()
        self._last_seq = 0

    @property
    def writer_pid(self) -> int:
        return self._map.q(H_WRITER_PID)

    def latest(self, copy: bool = False) -> Frame | None:
        """The newest committed frame, or None if none was published yet."""
        for _ in range(self._max_retries):
            frame = self._try_read(copy)
            if frame is not _RETRY:
                if frame is not None:
                    self._last_seq = max(self._last_seq, frame.seq)
                return frame
            time.sleep(0)
        raise TimeoutError("frame ring kept changing while reading")

    def wait_next(self, timeout: float | None = None, copy: bool = False) -> Frame | None:
        """Blocks until a frame newer than the last one this reader returned; None on timeout."""
        deadline = None if timeout is None else time.monotonic() + timeout
        last = self._last_seq
        while True:
            frame = self.latest(copy)
            if frame is not None and frame.seq > last:
                return frame
            remaining = None if deadline is None else deadline - time.monotonic()
            if remaining is not None and remaining <= 0:
                return None
            self._wait_for_commit(last, remaining)

    def close(self) -> None:
        """Drops this reader's mapping (frames already handed out keep theirs alive)."""
        self._map = None

    def __enter__(self) -> "FrameReader":
        return self

    def __exit__(self, *exc: Any) -> None:
        self.close()

    # ------------------------------------------------------------------ internals

    def _wait_for_commit(self, after_seq: int, remaining: float | None) -> None:
        client = self._client
        if client is not None and not client.closed:
            client.wait_frame(after_seq, remaining)
        else:
            time.sleep(self._poll_interval if remaining is None else min(self._poll_interval, remaining))

    def _current_mapping(self) -> _Mapping | None:
        """The mapping for the live generation, remapping after growth; None while it changes."""
        mapping = self._map
        if mapping is None:
            raise ValueError("FrameReader is closed")
        generation = mapping.q(H_GENERATION)
        if generation & 1:
            return None
        if generation != mapping.generation:
            if not mapping.load_layout():
                mapping = _Mapping(self.path)
                if not mapping.load_layout():
                    return None
            self._map = mapping
        return mapping

    def _try_read(self, copy: bool):
        mapping = self._current_mapping()
        if mapping is None:
            return _RETRY
        latest = mapping.q(H_LATEST)
        if latest == 0:
            return None if mapping.q(H_GENERATION) == mapping.generation else _RETRY
        commit, slot = latest >> LATEST_SLOT_BITS, latest & LATEST_SLOT_MASK
        if slot >= mapping.slot_count:
            return _RETRY
        base = mapping.slot_base(slot)
        seq = mapping.q(base + S_SEQ)
        if seq & 1 or mapping.q(base + S_COMMIT) != commit:
            return _RETRY
        frame = self._read_slot(mapping, base, slot, seq, commit, copy)
        if frame is None or not self._unchanged_in(mapping, base, seq):
            return _RETRY
        return frame

    def _read_slot(self, mapping: _Mapping, base: int, slot: int, seq: int, commit: int,
                   copy: bool) -> Frame | None:
        infos = self._stream_table(mapping, base)
        if infos is None:
            return None
        streams = {info.name: self._array(mapping, base, info, copy) for info in infos.values()}
        meta = self._meta(mapping, base)
        if meta is _RETRY:
            return None
        return Frame(self, mapping.generation, seq, index=_signed(mapping.q(base + S_FRAME_INDEX)),
                     capture_nanos=_signed(mapping.q(base + S_CAPTURE_NANOS)), seq=commit, slot=slot,
                     streams=streams, infos=infos, meta=meta, copied=copy)

    @staticmethod
    def _stream_table(mapping: _Mapping, base: int) -> dict[str, StreamInfo] | None:
        count = mapping.d(base + S_STREAM_COUNT)
        if count > 16:
            return None
        infos = {}
        for index in range(count):
            entry = base + S_STREAM_TABLE + index * STREAM_ENTRY_SIZE
            try:
                info = StreamInfo(
                    name=mapping.text(entry + E_NAME, NAME_BYTES),
                    dtype=np.dtype(mapping.text(entry + E_DTYPE, DTYPE_BYTES)),
                    channels=mapping.d(entry + E_CHANNELS),
                    width=mapping.d(entry + E_WIDTH),
                    height=mapping.d(entry + E_HEIGHT),
                    offset=mapping.q(entry + E_OFFSET),
                    size=mapping.q(entry + E_SIZE))
            except (UnicodeDecodeError, TypeError):
                return None
            if info.offset + info.size > mapping.slot_size or info.size != _expected_size(info):
                return None
            infos[info.name] = info
        return infos

    @staticmethod
    def _array(mapping: _Mapping, base: int, info: StreamInfo, copy: bool) -> np.ndarray:
        count = info.height * info.width * info.channels
        view = np.frombuffer(mapping.mm, dtype=info.dtype, count=count, offset=base + info.offset)
        view = view.reshape(info.shape)
        return view.copy() if copy else view

    @staticmethod
    def _meta(mapping: _Mapping, base: int):
        size = mapping.d(base + S_META_SIZE)
        if size == 0:
            return {}
        if size > mapping.d(H_META_CAPACITY):
            return _RETRY
        try:
            return msgpack.unpackb(mapping.mm[base + S_META:base + S_META + size], raw=False)
        except Exception:  # noqa: BLE001 - torn meta: retry
            return _RETRY

    def _unchanged(self, generation: int, slot: int, seq: int) -> bool:
        mapping = self._map
        if mapping is None or mapping.generation != generation:
            return False
        return self._unchanged_in(mapping, mapping.slot_base(slot), seq)

    @staticmethod
    def _unchanged_in(mapping: _Mapping, base: int, seq: int) -> bool:
        return mapping.q(base + S_SEQ) == seq and mapping.q(H_GENERATION) == mapping.generation


def _expected_size(info: StreamInfo) -> int:
    return info.height * info.width * info.channels * info.dtype.itemsize


def _signed(value: int) -> int:
    return value - (1 << 64) if value >= 1 << 63 else value
