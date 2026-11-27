"""Decodes the streams the Java encoder test wrote and compares them with the reference frames.

Run ``./gradlew encoderTest`` first, then ``python -m pytest python/tests/test_episode_streams.py``
(or run this file directly).
"""

from pathlib import Path

import numpy as np

from opencrafter.episode import ArrayStream

STREAMS = Path(__file__).resolve().parents[2] / "build" / "encoder-test-run" / "streams"
W, H, FRAMES = 37, 11, 3


def _flow(frame: int) -> np.ndarray:
    return np.fromfile(STREAMS / f"flow-{frame}.f32", dtype="<f4").reshape(H, W, 2)


def _mask(frame: int) -> np.ndarray:
    return np.fromfile(STREAMS / f"mask-{frame}.u8", dtype=np.uint8).reshape(H, W, 1).astype(bool)


def test_f32_is_exact():
    stream = ArrayStream(STREAMS / "f32.ocs")
    assert len(stream) == FRAMES
    for f in range(FRAMES):
        assert np.array_equal(stream[f], _flow(f))


def test_f16_is_half_precision():
    stream = ArrayStream(STREAMS / "f16.ocs")
    for f in range(FRAMES):
        assert np.array_equal(stream[f], _flow(f).astype(np.float16).astype(np.float32))


def test_q16_is_sixteenth_pixel():
    stream = ArrayStream(STREAMS / "q16.ocs")
    for f in range(FRAMES):
        assert np.abs(stream[f] - _flow(f)).max() <= 0.5 / 16 + 1e-6


def test_bits():
    stream = ArrayStream(STREAMS / "bits.ocs")
    for f in range(FRAMES):
        assert np.array_equal(stream[f], _mask(f))


if __name__ == "__main__":
    for test in (test_f32_is_exact, test_f16_is_half_precision, test_q16_is_sixteenth_pixel, test_bits):
        test()
        print("ok", test.__name__)
