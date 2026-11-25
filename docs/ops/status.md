# Status sensor

Sensor `status` (readable with `sensor.read {names: ["status"]}`) and op `status.read`
(`ThreadAffinity.MAIN`, no args) return the same plain dict, read on the main thread.

Python: `opencrafter.status.Status(client).read()`.

```text
{
  "window": {
    "width": int, "height": int,              # framebuffer = client area in pixels (no OS title bar);
                                              # same size as the vision frames
    "screen_width": int, "screen_height": int,# window size in OS logical units (differs on HiDPI)
    "gui_scale": int, "gui_width": int, "gui_height": int,  # GUI scale and scaled GUI size
    "focused": bool
  },
  "screen": str | null,                       # class name of the open screen, e.g. "InventoryScreen"
  "paused": bool,                             # singleplayer game paused
  "in_world": bool,                           # false: none of the keys below are present
  "game_mode": "survival" | "creative" | "adventure" | "spectator",
  "dead": bool,
  "selected_slot": int,                       # hotbar 0-8
  "rotation": {"yaw": float, "pitch": float, "yaw_raw": float},
  "health": {"current": float, "max": float, "absorption": float},
  "food": {"level": int, "saturation": float},
  "air": {
    "supply": int, "max": int,                # air supply in ticks (vanilla max 300)
    "eyes_in_water": bool,
    "hud_shown": bool,                        # the bubble row is drawn right now
    "bubbles": int,                           # full bubbles drawn (0-10)
    "bubble_popping": bool,                   # the next bubble is drawn popping
    "bubbles_empty": int                      # empty bubbles drawn
  },
  "effects": {
    "<effect id>": {                          # e.g. "minecraft:speed"
      "amplifier": int, "level": int,         # level = amplifier + 1
      "duration_ticks": int,                  # -1 = infinite
      "ambient": bool, "visible": bool, "show_icon": bool
    }
  }
}
```

- `yaw` is wrapped to [-180, 180) (0 = south, 90 = west, like F3); `yaw_raw` is the unwrapped
  entity value that keeps accumulating while turning; `pitch` is in [-90, 90], positive = down.
- Air bubbles follow `Hud.extractAirBubbles` exactly: `bubbles = ceil((supply - 2) * 10 / max)`,
  `bubbles_empty = 10 - ceil((supply + d) * 10 / max)` with `d = 1` while submerged and air left,
  else 0. `hud_shown` requires a game mode that can take damage, the HUD not hidden (F1) and
  either eyes in water or air below max. The counts are computed even when not shown.
