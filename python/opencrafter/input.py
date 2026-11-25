"""Thin wrappers for the ``input.*`` ops (see docs/ops/input.md).

``client`` is any object with ``call(op, **args)`` (request/response) and
``send(op, **args)`` (fire-and-forget, no response).
"""

from __future__ import annotations

from typing import Any, Iterable, Optional


class Input:
    def __init__(self, client: Any) -> None:
        self._client = client

    # Look / hotbar -----------------------------------------------------------

    def rotate(self, yaw: float = 0.0, pitch: float = 0.0, *, wait: bool = False) -> Optional[dict]:
        """Turn by (yaw, pitch) degrees relative to the current view, like mouse look.

        Positive yaw turns right, positive pitch looks down. Returns the new
        ``{"yaw", "pitch"}`` when ``wait`` is true.
        """
        return self._dispatch(wait, "input.rotate", yaw=float(yaw), pitch=float(pitch))

    def hotbar(self, slot: int) -> dict:
        """Select hotbar slot 0-8."""
        return self._client.call("input.hotbar", slot=int(slot))

    # Key mappings ------------------------------------------------------------

    def hold(self, keys: Iterable[str], lease_ms: Optional[int] = None) -> None:
        """Declare exactly which key mappings are held right now (stream this every tick/frame).

        Keys not listed that an earlier ``hold`` held are released; listed keys
        are released automatically if not re-asserted within the lease.
        """
        args: dict = {"down": list(keys)}
        if lease_ms is not None:
            args["lease_ms"] = int(lease_ms)
        self._client.send("input.keys", **args)

    def press(self, key: str, *, wait: bool = False) -> None:
        """One click: key down, released after one client tick."""
        self._dispatch(wait, "input.key", key=key, action="press")

    def down(self, key: str, duration_ms: Optional[int] = None, *, wait: bool = False) -> None:
        """Hold a key for ``duration_ms`` (default: the hold lease)."""
        args: dict = {"key": key, "action": "down"}
        if duration_ms is not None:
            args["duration_ms"] = int(duration_ms)
        self._dispatch(wait, "input.key", **args)

    def up(self, key: str, *, wait: bool = False) -> None:
        """Release a key held by any input op."""
        self._dispatch(wait, "input.key", key=key, action="up")

    def release_all(self) -> None:
        self._client.call("input.release_all")

    def keys(self) -> list:
        """All key mappings: id, short, category, key, key_label, down, toggle_mode, simulated."""
        return self._client.call("input.keys.list")

    # Mouse buttons are the attack / use key mappings (rebinding is honored).

    def attack(self, *, wait: bool = False) -> None:
        self.press("attack", wait=wait)

    def use(self, *, wait: bool = False) -> None:
        self.press("use", wait=wait)

    # Raw keyboard -----------------------------------------------------------

    def screen_key(self, key: str, modifiers: Iterable[str] = (), *, wait: bool = True) -> None:
        """Deliver a physical key press (e.g. "escape", "enter", "backspace", "page_up")."""
        self._dispatch(wait, "input.screen_key", key=key, modifiers=list(modifiers))

    def type(self, text: str) -> dict:
        """Type text into the focused text field of the open screen."""
        return self._client.call("input.type", text=text)

    # -------------------------------------------------------------------------

    def _dispatch(self, wait: bool, op: str, **args: Any) -> Any:
        if wait:
            return self._client.call(op, **args)
        self._client.send(op, **args)
        return None
