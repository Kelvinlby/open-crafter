"""``python -m opencrafter``: list running games and where they were looked for."""
from __future__ import annotations

import argparse
import dataclasses
import json

from .discovery import discover, discovery_dirs


def main() -> int:
    parser = argparse.ArgumentParser(prog="python -m opencrafter", description=__doc__)
    parser.add_argument("--json", action="store_true", help="print the games as JSON (tokens included)")
    args = parser.parse_args()
    games = discover()
    if args.json:
        print(json.dumps([dataclasses.asdict(game) for game in games], indent=2))
        return 0
    for game in games:
        print(f"{game.instance}  pid {game.pid}  {game.endpoint}\n    {game.game_dir}")
    if not games:
        print("No running game found. Searched:")
        print("\n".join(f"  {path}" for path in discovery_dirs()))
    return 0 if games else 1


if __name__ == "__main__":
    raise SystemExit(main())
