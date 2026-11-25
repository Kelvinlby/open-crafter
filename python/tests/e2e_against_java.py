"""End-to-end check of the Python client against a live Java transport.

Run by the JUnit test ``PythonClientTest`` (which hosts the ops used here):

    python tests/e2e_against_java.py <socket-path>

Exits non-zero with a traceback on the first failed check.
"""
from __future__ import annotations

import sys
import threading
import time

import numpy as np

from opencrafter import BatchError, Client, RemoteError

EXPECTED_OPS = {"echo", "main.thread", "boom", "test.frame", "test.event", "test.counter"}


def check_requests(client: Client) -> None:
    assert EXPECTED_OPS <= set(client.ops), client.ops
    assert client.hello["protocol"] == 1
    payload = {"nested": {"list": [1, 2.5, "x", None, True], "bin": b"\x00\x01"}, "np": np.int32(7)}
    echoed = client.call("echo", **payload)
    assert echoed["nested"] == payload["nested"], echoed
    assert echoed["np"] == 7
    assert client.call("main.thread") == "fake-main"
    try:
        client.call("boom")
        raise AssertionError("boom did not raise")
    except RemoteError as error:
        assert error.message == "kaboom" and error.op == "boom", error
    try:
        client.call("no.such.op")
        raise AssertionError("unknown op did not raise")
    except RemoteError as error:
        assert error.message == "Unknown op: no.such.op", error


def check_batches(client: Client) -> None:
    results = client.batch(("echo", {"a": 1}), "main.thread", {"op": "echo", "args": {"b": 2}})
    assert results == [{"a": 1}, "fake-main", {"b": 2}], results
    try:
        client.batch("echo", "boom")
        raise AssertionError("failing batch did not raise")
    except BatchError as error:
        assert error.results[0]["ok"] and not error.results[1]["ok"], error.results
    envelopes = client.batch("boom", raise_on_error=False)
    assert envelopes == [{"ok": False, "error": "kaboom"}], envelopes


def check_fire_and_forget_order(client: Client) -> None:
    start = client.call("test.counter")
    for _ in range(100):
        client.send("test.counter")
    client.send_batch("test.counter", "test.counter")
    assert client.call("test.counter") == start + 103


def check_async(client: Client) -> None:
    futures = [client.call_async("echo", i=i) for i in range(50)]
    assert [f.result(timeout=5)["i"] for f in futures] == list(range(50))


def check_events(client: Client, background: bool) -> None:
    received = []
    arrived = threading.Event()

    def on_ping(data):
        received.append(data)
        arrived.set()

    seen_any = []
    client.on("ping", on_ping)
    client.on("*", lambda event, data: seen_any.append(event))
    client.call("test.event", value=42)
    if background:
        assert arrived.wait(5)
    else:
        deadline = time.monotonic() + 5
        while not arrived.is_set() and time.monotonic() < deadline:
            client.poll(0.05)
    assert received == [{"value": 42}], received
    assert "ping" in seen_any
    client.off("ping", on_ping)


def expected_rgb(width: int, height: int, index: int) -> np.ndarray:
    y, x = np.mgrid[0:height, 0:width]
    base = (x + y * width + index) % 251
    return np.stack([base, base + 1, base + 2, np.full_like(base, 255)], axis=-1).astype(np.uint8)


def check_frame(client: Client, width: int, height: int, index: int) -> None:
    reader = client.frames()
    reader.latest()  # marks frames from earlier runs as seen
    client.call("test.frame", width=width, height=height, index=index)
    frame = reader.wait_next(timeout=5)
    assert frame is not None, "no frame arrived"
    assert frame.index == index, frame
    assert frame.meta == {"index": index, "size": [width, height]}, frame.meta
    rgb, flow, occluded = frame["rgb"], frame["flow"], frame["flow_occluded"]
    assert rgb.shape == (height, width, 4) and rgb.dtype == np.uint8
    assert flow.shape == (height, width, 2) and flow.dtype == np.float32
    assert occluded.shape == (height, width, 1)
    assert not rgb.flags.writeable, "views into shared memory must be read-only"
    np.testing.assert_array_equal(rgb, expected_rgb(width, height, index))
    y, x = np.mgrid[0:height, 0:width]
    np.testing.assert_array_equal(flow[..., 0], x.astype(np.float32))
    np.testing.assert_array_equal(flow[..., 1], -y.astype(np.float32))
    assert int(occluded.sum()) == height
    assert frame.valid()
    copied = reader.latest(copy=True)
    assert copied is not None and copied.seq == frame.seq and copied.valid()
    assert reader.wait_next(timeout=0.05) is None, "no newer frame expected"


def check_frames(client: Client) -> None:
    check_frame(client, 64, 32, 1)
    check_frame(client, 64, 32, 2)
    check_frame(client, 320, 200, 3)   # forces the ring to grow and the reader to remap
    check_frame(client, 16, 8, 4)      # smaller frames reuse the grown slots


def run(socket_path: str, background: bool) -> None:
    with Client(socket_path, background=background, timeout=5) as client:
        check_requests(client)
        check_batches(client)
        check_fire_and_forget_order(client)
        if background:
            check_async(client)
        check_events(client, background)
        check_frames(client)
    assert client.closed


def main() -> int:
    socket_path = sys.argv[1]
    for background in (True, False):
        run(socket_path, background)
        print(f"background={background}: OK")
    return 0


if __name__ == "__main__":
    sys.exit(main())
