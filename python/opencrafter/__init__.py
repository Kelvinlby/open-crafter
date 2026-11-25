"""Python backend client for the open-crafter Minecraft mod.

``Client`` talks to the game over its Unix domain socket; ``FrameReader`` reads frames
from shared memory. Feature modules add thin wrappers as ``opencrafter.<module>``.
"""
from .client import BatchError, Client, ConnectionClosed, RemoteError, default_socket_path
from .frames import Frame, FrameReader, RingFormatError, StreamInfo

__all__ = [
    "BatchError",
    "Client",
    "ConnectionClosed",
    "Frame",
    "FrameReader",
    "RemoteError",
    "RingFormatError",
    "StreamInfo",
    "default_socket_path",
]
