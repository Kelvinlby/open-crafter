#!/usr/bin/env python3
"""
Template script for sending a JSON-RPC command to the OpenCrafter mod
over its Unix domain socket.

Fill in COMMAND and ARGS below, then run:  python3 send_command.py
"""

import json
import socket
import sys

# -----------------------------------------------------------------------------
# Configuration — customize as needed
# -----------------------------------------------------------------------------

SOCKET_PATH = "run/open-crafter/connector.socket"

COMMAND = "esc"
ARGS = {}

# -----------------------------------------------------------------------------

def send_command(command: str, args: dict, socket_path: str = SOCKET_PATH):
    """
    Sends a JSON-RPC 2.0 request to the mod and returns the parsed response.

    The server expects positional params (JsonArray), so the dict's values
    are sent in insertion order. In Python 3.7+ dict order is preserved, so
    declare ARGS with keys in the same order as the command's ParamDef list.
    """
    request = {
        "jsonrpc": "2.0",
        "method": command,
        "params": list(args.values()),
        "id": 1,
    }
    payload = (json.dumps(request) + "\n").encode("utf-8")

    with socket.socket(socket.AF_UNIX, socket.SOCK_STREAM) as sock:
        sock.connect(socket_path)
        sock.sendall(payload)

        buffer = b""
        while b"\n" not in buffer:
            chunk = sock.recv(65536)
            if not chunk:
                break
            buffer += chunk

    line = buffer.split(b"\n", 1)[0].decode("utf-8")
    return json.loads(line)


def main():
    try:
        response = send_command(COMMAND, ARGS)
    except FileNotFoundError:
        print(f"Socket not found at {SOCKET_PATH}. Is the mod running?", file=sys.stderr)
        sys.exit(1)
    except ConnectionRefusedError:
        print(f"Connection refused at {SOCKET_PATH}.", file=sys.stderr)
        sys.exit(1)

    print(json.dumps(response, indent=2))

    if "error" in response:
        sys.exit(1)


if __name__ == "__main__":
    main()
