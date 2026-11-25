"""Finding running games: discovery files, endpoints and the standard environment variables.

Every running game writes ``<instance>.json`` into a per-user discovery directory:

* Linux: ``$XDG_RUNTIME_DIR/open-crafter``
* Linux, game in a Flatpak (e.g. Prism Launcher): ``$XDG_RUNTIME_DIR/app/<flatpak id>/open-crafter``
* macOS, Windows (and Linux without ``XDG_RUNTIME_DIR``): ``<temp dir>/open-crafter``

``Client()`` with no arguments connects to the newest live game found there. Manual
overrides, highest priority first:

* ``Client("unix:/path/to.sock")`` / ``Client("tcp:127.0.0.1:25599", token=...)``
* ``OPENCRAFTER_ENDPOINT`` (+ ``OPENCRAFTER_TOKEN`` for TCP); ``OPENCRAFTER_SOCKET`` (a socket path) also works
* ``OPENCRAFTER_INSTANCE``: pick among several games by instance id, pid or a part of the game directory
* ``OPENCRAFTER_DISCOVERY_DIR``: search this directory (``os.pathsep``-separated list) first
"""
from __future__ import annotations

import dataclasses
import glob
import json
import logging
import os
import sys
import tempfile
from dataclasses import dataclass
from typing import Iterable

__all__ = ["Endpoint", "Game", "discover", "discovery_dirs", "resolve"]

ENV_ENDPOINT = "OPENCRAFTER_ENDPOINT"
ENV_SOCKET = "OPENCRAFTER_SOCKET"
ENV_TOKEN = "OPENCRAFTER_TOKEN"
ENV_INSTANCE = "OPENCRAFTER_INSTANCE"
ENV_DISCOVERY_DIR = "OPENCRAFTER_DISCOVERY_DIR"
DIRECTORY_NAME = "open-crafter"

_log = logging.getLogger("opencrafter")


@dataclass(frozen=True)
class Endpoint:
    """``unix:<path>`` or ``tcp:<host>:<port>``; a bare string is a Unix socket path."""

    kind: str
    path: str | None = None
    host: str | None = None
    port: int | None = None

    @classmethod
    def parse(cls, text: str) -> "Endpoint":
        if text.startswith("tcp:"):
            host, _, port = text[4:].rpartition(":")
            if not host or not port.isdigit():
                raise ValueError(f"expected tcp:<host>:<port>, got {text!r}")
            return cls("tcp", host=host, port=int(port))
        return cls("unix", path=text[5:] if text.startswith("unix:") else text)

    def __str__(self) -> str:
        return f"unix:{self.path}" if self.kind == "unix" else f"tcp:{self.host}:{self.port}"


@dataclass(frozen=True)
class Game:
    """One running game, as announced by its discovery file."""

    instance: str
    pid: int
    endpoint: str
    token: str | None
    shm: str | None
    started_ms: int
    game_dir: str
    flatpak: str | None
    file: str

    def matches(self, selector: str) -> bool:
        """True for the instance id, the pid, or a case-insensitive part of the game directory."""
        lowered = selector.lower()
        return selector in (self.instance, str(self.pid)) or lowered in self.game_dir.lower()


def discovery_dirs() -> list[str]:
    """Directories searched for discovery files, in priority order."""
    candidates: list[str] = []
    configured = os.environ.get(ENV_DISCOVERY_DIR)
    if configured:
        candidates += [path for path in configured.split(os.pathsep) if path]
    runtime_dir = os.environ.get("XDG_RUNTIME_DIR")
    if runtime_dir:
        candidates.append(os.path.join(runtime_dir, DIRECTORY_NAME))
        candidates += sorted(glob.glob(os.path.join(glob.escape(runtime_dir), "app", "*", DIRECTORY_NAME)))
    candidates += [os.path.join(base, DIRECTORY_NAME) for base in _temp_dirs()]
    return list(dict.fromkeys(os.path.abspath(path) for path in candidates))


def discover() -> list[Game]:
    """Live games, newest first."""
    games = [game for directory in discovery_dirs() for game in _read_directory(directory)]
    unique = {game.file: game for game in games}.values()
    return sorted(unique, key=lambda game: game.started_ms, reverse=True)


def resolve(endpoint: str | None = None, token: str | None = None) -> tuple[Endpoint, str | None]:
    """The endpoint and token to connect to: explicit, then environment, then discovery."""
    token = token or os.environ.get(ENV_TOKEN)
    explicit = endpoint or os.environ.get(ENV_ENDPOINT) or os.environ.get(ENV_SOCKET)
    if explicit:
        return Endpoint.parse(explicit), token
    game = _choose(discover())
    return Endpoint.parse(game.endpoint), token or game.token


def _choose(games: list[Game]) -> Game:
    selector = os.environ.get(ENV_INSTANCE)
    candidates = [game for game in games if game.matches(selector)] if selector else games
    if not candidates:
        raise ConnectionError(_not_found_message(games, selector))
    if len(candidates) > 1:
        others = ", ".join(f"{game.instance} ({game.game_dir})" for game in candidates[1:])
        _log.info("Several games are running; using the newest. Others: %s (pick with %s)", others, ENV_INSTANCE)
    return candidates[0]


def _not_found_message(games: list[Game], selector: str | None) -> str:
    if selector:
        running = ", ".join(f"{game.instance} ({game.game_dir})" for game in games) or "none"
        return f"no running game matches {ENV_INSTANCE}={selector!r}; running: {running}"
    searched = "\n  ".join(discovery_dirs())
    return ("no running open-crafter game found. Is Minecraft running with the mod? Searched:\n  "
            f"{searched}\nIn game, /opencrafter transport status shows the endpoint; pass it as "
            f"Client('<endpoint>') or set {ENV_ENDPOINT}.")


def _read_directory(directory: str) -> Iterable[Game]:
    for file in glob.glob(os.path.join(glob.escape(directory), "*.json")):
        game = _read_game(file)
        if game is not None and _is_live(file[:-len(".json")] + ".lock"):
            yield game


def _read_game(file: str) -> Game | None:
    try:
        with open(file, encoding="utf-8") as handle:
            data = json.load(handle)
        fields = {field.name for field in dataclasses.fields(Game)} - {"file"}
        return Game(file=file, **{name: data.get(name) for name in fields})
    except (OSError, ValueError, TypeError):
        return None


def _is_live(lock_file: str) -> bool:
    """The game holds its lock file locked for its whole life; a lockable (or missing) one is stale."""
    try:
        fd = os.open(lock_file, os.O_RDWR)
    except FileNotFoundError:
        return False
    except OSError:
        return True
    try:
        return not _try_lock_and_release(fd)
    finally:
        os.close(fd)


def _try_lock_and_release(fd: int) -> bool:
    try:
        if sys.platform == "win32":
            import msvcrt
            msvcrt.locking(fd, msvcrt.LK_NBLCK, 1)
            os.lseek(fd, 0, os.SEEK_SET)
            msvcrt.locking(fd, msvcrt.LK_UNLCK, 1)
        else:
            import fcntl
            fcntl.lockf(fd, fcntl.LOCK_EX | fcntl.LOCK_NB)
            fcntl.lockf(fd, fcntl.LOCK_UN)
        return True
    except OSError:
        return False


def _temp_dirs() -> list[str]:
    """The temp dir Java uses: ``%TEMP%`` / ``$TMPDIR``, and on macOS the per-user one even without ``$TMPDIR``."""
    dirs = [tempfile.gettempdir()]
    if sys.platform == "darwin":
        try:
            dirs.append(os.confstr("CS_DARWIN_USER_TEMP_DIR"))
        except (ValueError, OSError):
            pass
    return [path for path in dirs if path]
