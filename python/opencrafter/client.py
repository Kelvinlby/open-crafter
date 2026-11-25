"""Control-channel client for open-crafter (see docs/PROTOCOL.md).

One Unix-domain-socket connection to the game. Requests are MessagePack maps framed by a
u32 little-endian length. Two reading modes:

* ``background=True`` (default): a daemon thread reads the socket, resolves replies and runs
  event callbacks. ``call()`` blocks on a plain lock, which is the cheapest wake-up CPython
  offers.
* ``background=False``: no thread at all. Whoever waits (``call``, ``wait_frame``,
  ``poll``) reads the socket inline, so a reply wakes the caller with zero thread hops.
  Events are dispatched only while someone is waiting or polling. Intended for a
  single-threaded control loop.
"""
from __future__ import annotations

import itertools
import logging
import os
import socket
import struct
import tempfile
import threading
import time
from concurrent.futures import Future
from typing import Any, Callable, Iterable

import msgpack

__all__ = [
    "BatchError",
    "Client",
    "ConnectionClosed",
    "RemoteError",
    "default_socket_path",
]

PROTOCOL_VERSION = 1
SOCKET_NAME = "open-crafter.sock"
MAX_MESSAGE_BYTES = 64 << 20

_LENGTH = struct.Struct("<I")
_DEFAULT = object()
_CLOSED = {"ok": False, "error": "connection closed"}
_log = logging.getLogger("opencrafter")


class RemoteError(Exception):
    """The game reported ``ok: false`` for a request."""

    def __init__(self, op: str, message: str):
        super().__init__(f"{op}: {message}")
        self.op = op
        self.message = message


class BatchError(RemoteError):
    """At least one call of a batch failed; ``results`` holds every per-call envelope."""

    def __init__(self, results: list[dict]):
        failures = [f"[{i}] {r.get('error')}" for i, r in enumerate(results) if not r.get("ok")]
        super().__init__("batch", "; ".join(failures))
        self.results = results


class ConnectionClosed(ConnectionError):
    """The socket closed while a request was outstanding (or before it was sent)."""


def default_socket_path() -> str:
    """``$OPENCRAFTER_SOCKET``, else ``$XDG_RUNTIME_DIR/open-crafter.sock``, else the temp dir."""
    configured = os.environ.get("OPENCRAFTER_SOCKET")
    if configured:
        return configured
    runtime_dir = os.environ.get("XDG_RUNTIME_DIR") or tempfile.gettempdir()
    return os.path.join(runtime_dir, SOCKET_NAME)


def _encode_extra(value: Any) -> Any:
    """Lets numpy scalars/arrays, tuples and sets be passed as arguments."""
    if hasattr(value, "tolist"):
        return value.tolist()
    if isinstance(value, (set, frozenset)):
        return list(value)
    raise TypeError(f"cannot encode {type(value).__name__}")


def _frame(message: dict) -> bytes:
    body = msgpack.packb(message, use_bin_type=True, default=_encode_extra)
    return _LENGTH.pack(len(body)) + body


def _normalize_calls(calls: Iterable[Any]) -> list[dict]:
    """Accepts ``"op"``, ``("op", {args})`` or ``{"op": ..., "args": ...}`` per call."""
    normalized = []
    for call in calls:
        if isinstance(call, str):
            normalized.append({"op": call, "args": {}})
        elif isinstance(call, dict):
            normalized.append({"op": call["op"], "args": call.get("args") or {}})
        else:
            op, *rest = call
            normalized.append({"op": op, "args": rest[0] if rest else {}})
    return normalized


class _Inbox:
    """Splits the byte stream into messages; partial data survives socket timeouts."""

    def __init__(self, sock: socket.socket):
        self._sock = sock
        self._buffer = bytearray(64 * 1024)
        self._start = 0
        self._end = 0

    def read(self) -> Any:
        self._fill(4)
        (length,) = _LENGTH.unpack_from(self._buffer, self._start)
        if length > MAX_MESSAGE_BYTES:
            raise ConnectionClosed(f"message length {length} out of range")
        self._fill(4 + length)
        begin = self._start + 4
        message = msgpack.unpackb(memoryview(self._buffer)[begin:begin + length], raw=False)
        self._start = begin + length
        if self._start == self._end:
            self._start = self._end = 0
        return message

    def _fill(self, count: int) -> None:
        while self._end - self._start < count:
            self._make_room(count)
            received = self._sock.recv_into(memoryview(self._buffer)[self._end:])
            if received == 0:
                raise ConnectionClosed("socket closed by the game")
            self._end += received

    def _make_room(self, count: int) -> None:
        if len(self._buffer) - self._start >= count:
            return
        pending = self._buffer[self._start:self._end]
        if count > len(self._buffer):
            self._buffer = bytearray(max(count, 2 * len(self._buffer)))
        self._buffer[:len(pending)] = pending
        self._start, self._end = 0, len(pending)


class _Waiter:
    """One-shot reply slot backed by a pre-acquired lock (cheaper than Event/Condition)."""

    __slots__ = ("_lock", "message")

    def __init__(self):
        self._lock = threading.Lock()
        self._lock.acquire()
        self.message: dict | None = None

    def set(self, message: dict) -> None:
        self.message = message
        self._lock.release()

    def wait(self, timeout: float | None) -> bool:
        return self._lock.acquire(timeout=-1 if timeout is None else max(timeout, 0.0))


class Client:
    """Connection to the game. Thread-safe for sending; see the module docstring for reading."""

    def __init__(self, path: str | None = None, *, background: bool = True,
                 timeout: float | None = 10.0, connect_timeout: float = 5.0):
        self.path = path or default_socket_path()
        self.timeout = timeout
        self.hello: dict = {}
        self._background = background
        self._sock = socket.socket(socket.AF_UNIX, socket.SOCK_STREAM)
        self._sock.settimeout(connect_timeout)
        self._sock.connect(self.path)
        self._sock.settimeout(None)
        self._inbox = _Inbox(self._sock)
        self._send_lock = threading.Lock()
        self._read_lock = threading.Lock()
        self._state_lock = threading.Lock()
        self._ids = itertools.count(1)
        self._pending: dict[int, Callable[[dict], None]] = {}
        self._callbacks: dict[str, list[Callable]] = {}
        self._frame_cond = threading.Condition()
        self._frame_seq = 0
        self._last_frame: dict | None = None
        self._hello_ready = threading.Event()
        self._closed = False
        self._frames = None
        self._start_reading(connect_timeout)

    # ----------------------------------------------------------------- properties

    @property
    def ops(self) -> list[str]:
        return list(self.hello.get("ops", []))

    @property
    def shm_path(self) -> str | None:
        return self.hello.get("shm")

    @property
    def closed(self) -> bool:
        return self._closed

    # ------------------------------------------------------------------- requests

    def call(self, op: str, /, **args: Any) -> Any:
        """Runs ``op`` and returns its result; raises RemoteError on failure."""
        return self.request(op, args)

    def request(self, op: str, args: dict | None = None, *, timeout: Any = _DEFAULT) -> Any:
        """Like ``call`` with an explicit args dict and per-call timeout (seconds or None)."""
        waiter = _Waiter()
        request_id = self._register(waiter.set)
        self._send({"id": request_id, "op": op, "args": args or {}}, request_id)
        self._await(waiter, request_id, op, self.timeout if timeout is _DEFAULT else timeout)
        return self._result(op, waiter.message)

    def call_async(self, op: str, /, **args: Any) -> Future:
        """Sends ``op`` and returns a Future (resolved by the reader; in sync mode by poll())."""
        future: Future = Future()
        request_id = self._register(lambda message: self._settle(future, op, message))
        self._send({"id": request_id, "op": op, "args": args}, request_id)
        return future

    def send(self, op: str, /, **args: Any) -> None:
        """Fire-and-forget: no reply, errors are only logged by the game."""
        self._send({"op": op, "args": args})

    def batch(self, *calls: Any, timeout: Any = _DEFAULT, raise_on_error: bool = True) -> list[Any]:
        """Runs calls in order within one game-thread drain; returns their results.

        With ``raise_on_error`` a failing call raises BatchError (holding all envelopes);
        otherwise the raw envelopes ``{"ok", "result" | "error"}`` are returned.
        """
        envelopes = self.request("batch", {"calls": _normalize_calls(calls)}, timeout=timeout)
        if not raise_on_error:
            return envelopes
        if not all(envelope.get("ok") for envelope in envelopes):
            raise BatchError(envelopes)
        return [envelope.get("result") for envelope in envelopes]

    def send_batch(self, *calls: Any) -> None:
        """Fire-and-forget batch."""
        self._send({"op": "batch", "args": {"calls": _normalize_calls(calls)}})

    # --------------------------------------------------------------------- events

    def on(self, event: str, callback: Callable | None = None):
        """Registers ``callback(data)`` for ``event`` (or ``callback(event, data)`` for ``"*"``).

        Usable as a decorator. Callbacks run on the reader thread (or the waiting thread in
        sync mode): keep them short.
        """
        if callback is None:
            return lambda fn: self.on(event, fn)
        with self._state_lock:
            self._callbacks.setdefault(event, []).append(callback)
        return callback

    def off(self, event: str, callback: Callable) -> None:
        with self._state_lock:
            listeners = self._callbacks.get(event, [])
            if callback in listeners:
                listeners.remove(callback)

    def poll(self, timeout: float = 0.0) -> None:
        """Sync mode: dispatches incoming messages for up to ``timeout`` seconds."""
        if self._background:
            if timeout > 0:
                time.sleep(timeout)
            return
        self._pump_until(lambda: False, timeout)

    def wait_frame(self, after_seq: int, timeout: float | None = None) -> dict | None:
        """Blocks until a ``frame`` event with ``seq > after_seq``; returns its data or None."""
        def arrived() -> bool:
            return self._frame_seq > after_seq or self._closed

        if self._background:
            with self._frame_cond:
                self._frame_cond.wait_for(arrived, timeout)
        else:
            self._pump_until(arrived, timeout)
        return self._last_frame if self._frame_seq > after_seq else None

    def frames(self):
        """The shared-memory FrameReader announced in ``hello`` (created once)."""
        if self._frames is None:
            from .frames import FrameReader
            if not self.shm_path:
                raise RuntimeError("the game did not announce a frame ring")
            self._frames = FrameReader(self.shm_path, client=self)
        return self._frames

    # -------------------------------------------------------------------- closing

    def close(self) -> None:
        self._shutdown(None)
        if self._frames is not None:
            self._frames.close()

    def __enter__(self) -> "Client":
        return self

    def __exit__(self, *exc: Any) -> None:
        self.close()

    # ------------------------------------------------------------------ internals

    def _start_reading(self, connect_timeout: float) -> None:
        if self._background:
            threading.Thread(target=self._read_loop, name="opencrafter-reader", daemon=True).start()
            ready = self._hello_ready.wait(connect_timeout)
        else:
            ready = self._pump_until(self._hello_ready.is_set, connect_timeout)
        if not ready or self._closed:
            self._shutdown(None)
            raise ConnectionClosed("no hello from the game")
        if self.hello.get("protocol") != PROTOCOL_VERSION:
            _log.warning("game speaks protocol %s, client %s", self.hello.get("protocol"), PROTOCOL_VERSION)

    def _register(self, resolve: Callable[[dict], None]) -> int:
        request_id = next(self._ids)
        self._pending[request_id] = resolve
        return request_id

    def _send(self, message: dict, request_id: int | None = None) -> None:
        data = _frame(message)
        try:
            with self._send_lock:
                if self._closed:
                    raise ConnectionClosed("connection is closed")
                self._sock.sendall(data)
        except OSError as error:
            if request_id is not None:
                self._pending.pop(request_id, None)
            self._shutdown(error)
            raise ConnectionClosed(str(error)) from error

    def _await(self, waiter: _Waiter, request_id: int, op: str, timeout: float | None) -> None:
        if self._background:
            done = waiter.wait(timeout)
        else:
            done = self._pump_until(lambda: waiter.message is not None, timeout)
        if not done:
            self._pending.pop(request_id, None)
            raise TimeoutError(f"{op} timed out after {timeout}s")

    @staticmethod
    def _result(op: str, message: dict) -> Any:
        if message is _CLOSED:
            raise ConnectionClosed(f"{op}: connection closed")
        if not message.get("ok"):
            raise RemoteError(op, str(message.get("error")))
        return message.get("result")

    def _settle(self, future: Future, op: str, message: dict) -> None:
        try:
            future.set_result(self._result(op, message))
        except Exception as error:  # noqa: BLE001 - delivered through the future
            future.set_exception(error)

    def _read_loop(self) -> None:
        try:
            while True:
                self._dispatch(self._inbox.read())
        except Exception as error:  # noqa: BLE001 - any failure ends the connection
            self._shutdown(error)

    def _pump_until(self, predicate: Callable[[], bool], timeout: float | None) -> bool:
        """Sync mode: reads and dispatches inline until ``predicate`` holds or time runs out."""
        deadline = None if timeout is None else time.monotonic() + timeout
        while not predicate():
            remaining = None if deadline is None else deadline - time.monotonic()
            if remaining is not None and remaining <= 0:
                return False
            if not self._read_lock.acquire(timeout=-1 if remaining is None else remaining):
                return predicate()
            try:
                if not predicate():
                    self._read_one(remaining)
            finally:
                self._read_lock.release()
        return True

    def _read_one(self, timeout: float | None) -> None:
        if self._closed:
            return
        self._sock.settimeout(timeout)
        try:
            message = self._inbox.read()
        except (socket.timeout, BlockingIOError):
            return
        except Exception as error:  # noqa: BLE001
            self._shutdown(error)
            return
        finally:
            if not self._closed:
                self._sock.settimeout(None)
        self._dispatch(message)

    def _dispatch(self, message: Any) -> None:
        if not isinstance(message, dict):
            return
        event = message.get("event")
        if event is not None:
            self._on_event(event, message.get("data"))
            return
        resolve = self._pending.pop(message.get("id"), None)
        if resolve is not None:
            resolve(message)

    def _on_event(self, event: str, data: Any) -> None:
        if event == "hello":
            self.hello = data or {}
            self._hello_ready.set()
        elif event == "frame":
            self._record_frame(data)
        with self._state_lock:
            specific = list(self._callbacks.get(event, ()))
            wildcard = list(self._callbacks.get("*", ()))
        for callback in specific:
            self._run_callback(callback, data)
        for callback in wildcard:
            self._run_callback(callback, event, data)

    def _record_frame(self, data: dict) -> None:
        with self._frame_cond:
            self._last_frame = data
            self._frame_seq = max(self._frame_seq, int(data.get("seq", 0)))
            self._frame_cond.notify_all()

    @staticmethod
    def _run_callback(callback: Callable, *args: Any) -> None:
        try:
            callback(*args)
        except Exception:  # noqa: BLE001 - a callback must not kill the reader
            _log.exception("event callback %r failed", callback)

    def _shutdown(self, error: BaseException | None) -> None:
        with self._state_lock:
            if self._closed:
                return
            self._closed = True
        if error is not None and not isinstance(error, ConnectionClosed):
            _log.debug("connection lost: %s", error)
        try:
            self._sock.shutdown(socket.SHUT_RDWR)
        except OSError:
            pass
        self._sock.close()
        self._fail_pending()
        self._hello_ready.set()
        with self._frame_cond:
            self._frame_cond.notify_all()

    def _fail_pending(self) -> None:
        while self._pending:
            _, resolve = self._pending.popitem()
            resolve(_CLOSED)
