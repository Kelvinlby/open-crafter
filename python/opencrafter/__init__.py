"""Python backend client for the open-crafter Minecraft mod.

``Client()`` finds a running game by itself (see ``opencrafter.discovery``) and talks to it
over a Unix domain socket or, on Windows, loopback TCP; ``FrameReader`` reads frames from
shared memory. ``python -m opencrafter`` lists running games. Feature modules add thin wrappers as ``opencrafter.<module>``.
"""
from .client import BatchError, Client, ConnectionClosed, RemoteError, default_socket_path
from .discovery import Endpoint, Game, discover
from .frames import Frame, FrameReader, RingFormatError, StreamInfo

__all__ = [
    "BatchError",
    "Client",
    "ConnectionClosed",
    "Endpoint",
    "Frame",
    "FrameReader",
    "Game",
    "RemoteError",
    "RingFormatError",
    "StreamInfo",
    "default_socket_path",
    "discover",
]
