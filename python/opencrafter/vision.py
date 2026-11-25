"""Vision sensor ops (see docs/ops/vision.md).

Thin wrappers over ``Client.call(op, **args)``. Frames themselves arrive through the
shared-memory ring; these functions only choose what is captured and how often.
"""

from typing import Any, Iterable, Optional

RGB = "rgb"
FLOW = "flow"
FLOW_OCCLUDED = "flow_occluded"
STREAMS = (RGB, FLOW, FLOW_OCCLUDED)

TICK = "tick"
FIXED_HZ = "fixed_hz"
MAX_FPS = "max_fps"
RATE_MODES = (TICK, FIXED_HZ, MAX_FPS)


def configure(
    client: Any,
    streams: Optional[Iterable[str]] = None,
    rate_mode: Optional[str] = None,
    fixed_hz: Optional[float] = None,
) -> dict:
    """Selects the published streams and/or the push rate; returns ``status()``.

    Omitted arguments keep their current value. The rate is persisted in the mod config,
    the stream selection is not (it resets to ``["rgb"]`` when the game restarts).
    """
    args = {}
    if streams is not None:
        args["streams"] = list(streams)
    if rate_mode is not None:
        args["rate_mode"] = rate_mode
    if fixed_hz is not None:
        args["fixed_hz"] = float(fixed_hz)
    return client.call("vision.configure", **args)


def status(client: Any) -> dict:
    """Current settings and capture counters of the vision sensor."""
    return client.call("vision.status")
