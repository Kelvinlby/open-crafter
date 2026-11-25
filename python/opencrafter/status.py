"""Thin wrapper for the status sensor (see docs/ops/status.md).

``client`` is any object with ``call(op, **args)``.
"""

from __future__ import annotations

from typing import Any


class Status:
    def __init__(self, client: Any) -> None:
        self._client = client

    def read(self) -> dict:
        """Window size, rotation, health, food, air and status effects in one dict."""
        return self._client.call("status.read")
