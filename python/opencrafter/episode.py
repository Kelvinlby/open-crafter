"""Reads episodes written by the recording encoder (``./gradlew encode``).

An episode folder holds:

- ``episode.json``      what was replayed and how (written last: its presence means complete)
- ``rgb.mkv``           the RGB video (decode with any video library, e.g. PyAV or imageio-ffmpeg)
- ``flow.ocs``          optical flow, float pixels (+x right, +y down), one frame per captured frame
- ``flow_occluded.ocs`` occlusion mask of the flow (True = ignore that pixel's flow)
- ``frames.jsonl.zst``  one JSON object per frame: camera, ticks, sensors, the recorded client's
                        input since the previous frame (``replay``), interaction events, ...

``.ocs`` streams are random access::

    ep = Episode("datasets/2026-08-29_10-43-24_Noth1ngLeft-...")
    flow = ep.stream("flow")[42]        # (H, W, 2) float32
    for meta in ep.frames():
        meta["replay"]["keys"]
"""

from __future__ import annotations

import io
import json
import struct
from pathlib import Path
from typing import Iterator

import numpy as np

_MAGIC = b"OCSTREAM1\n"
_INDEX_MAGIC = b"OCSINDEX"


def _zstd():
    try:
        import zstandard
    except ImportError as e:  # pragma: no cover - depends on the environment
        raise ImportError("reading episodes needs the 'zstandard' package: pip install zstandard") from e
    return zstandard


class ArrayStream:
    """One ``.ocs`` stream: ``len(stream)`` frames, ``stream[i]`` decodes frame i to numpy."""

    def __init__(self, path: str | Path):
        self.path = Path(path)
        self._file = open(self.path, "rb")
        if self._file.read(len(_MAGIC)) != _MAGIC:
            raise ValueError(f"{self.path} is not an open-crafter array stream")
        (header_length,) = struct.unpack(">I", self._file.read(4))
        self.header = json.loads(self._file.read(header_length))
        self._file.seek(-(4 + 8 + len(_INDEX_MAGIC)), io.SEEK_END)
        frames, index_offset = struct.unpack(">IQ", self._file.read(12))
        if self._file.read(len(_INDEX_MAGIC)) != _INDEX_MAGIC:
            raise ValueError(f"{self.path} has no index (unfinished?)")
        self._file.seek(index_offset)
        self._offsets = np.frombuffer(self._file.read(8 * frames), dtype=">u8")
        self._decompressor = _zstd().ZstdDecompressor()
        self.width = self.header["width"]
        self.height = self.header["height"]
        self.channels = self.header["channels"]

    def __len__(self) -> int:
        return len(self._offsets)

    def __getitem__(self, index: int) -> np.ndarray:
        if index < 0:
            index += len(self)
        self._file.seek(int(self._offsets[index]))
        (length,) = struct.unpack(">I", self._file.read(4))
        packed = self._decompressor.decompress(self._file.read(length))
        return self._decode(packed)

    def __iter__(self) -> Iterator[np.ndarray]:
        for i in range(len(self)):
            yield self[i]

    def _decode(self, packed: bytes) -> np.ndarray:
        h, w, c = self.height, self.width, self.channels
        raw = np.frombuffer(packed, dtype=np.uint8)
        encoding = self.header["encoding"]
        if encoding == "bits":
            return np.unpackbits(raw)[: h * w * c].astype(bool).reshape(h, w, c)
        size = {"f16-delta": 2, "q16-delta": 2, "f32-delta": 4}[encoding]
        utype = np.dtype("<u2") if size == 2 else np.dtype("<u4")
        # Byte planes -> per-channel planes of row deltas -> running sum along each row (mod 2^bits).
        deltas = raw.reshape(c, size, h * w).transpose(0, 2, 1).copy().view(utype).reshape(c, h, w)
        values = np.cumsum(deltas, axis=2, dtype=utype).transpose(1, 2, 0)
        if encoding == "f16-delta":
            return values.view("<f2").astype(np.float32)
        if encoding == "f32-delta":
            return values.view("<f4")
        return values.view("<i2").astype(np.float32) / self.header["scale"]

    def close(self) -> None:
        self._file.close()


class Episode:
    """An encoded episode folder."""

    def __init__(self, path: str | Path):
        self.path = Path(path)
        summary = self.path / "episode.json"
        if not summary.exists():
            raise FileNotFoundError(f"{self.path} is not a finished episode (no episode.json)")
        self.info = json.loads(summary.read_text())

    @property
    def fps(self) -> float:
        return self.info["config"]["fps"]

    def stream(self, name: str) -> ArrayStream:
        """``flow`` or ``flow_occluded``."""
        return ArrayStream(self.path / f"{name}.ocs")

    def frames(self) -> Iterator[dict]:
        """Per-frame metadata, in frame order."""
        with open(self.path / "frames.jsonl.zst", "rb") as raw:
            reader = io.TextIOWrapper(_zstd().ZstdDecompressor().stream_reader(raw), encoding="utf-8")
            for line in reader:
                yield json.loads(line)

    @property
    def video(self) -> Path:
        return self.path / self.info["config"]["rgb"]["file"]
