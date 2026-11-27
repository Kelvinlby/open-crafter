# CLAUDE.md

## Project

open-crafter is a Minecraft mod for the **Fabric** loader, targeting **Minecraft 26.3** (Java 25, Fabric Loader >= 0.20.1, Fabric API).

- Build: `./gradlew build` → jars in `build/libs/`
- Version defaults to `dev`; override with `./gradlew build -Pversion=1.0.0`. CI builds use the short git hash as the version.
- Releases: the manual `publish` workflow (`.github/workflows/publish.yml`) takes a version without "v", builds the mod and the Python package (`python/`, PyPI `opencrafter`) with it, publishes to PyPI and creates the GitHub release `v<version>`. `python/pyproject.toml` keeps `version = "0.0.0"`; never bump it by hand.

## Recording encoder (not part of the mod)

- `src/encoder` (+ `src/encoderTest`) is a separate source set and Fabric mod (`open-crafter-encoder`) that replays packet recordings in headless worker clients and writes datasets: `./gradlew encode -Pinput=... -Poutput=...`, tests with `./gradlew encoderTest`. See `docs/encoder.md`.
- Never move encoder code or dependencies (Via, zstd-jni) into `src/main`; the mod only offers generic extension points (`open-crafter:modules`, `open-crafter:frames`).
- Workers render with `SDL_VIDEO_DRIVER=offscreen` (GPU, no window). Never let Gradle run `runEncoderWorker` itself: it opens a visible window.

## Compatibility requirements

- Every feature must work regardless of Minecraft's rendering backend (**OpenGL or Vulkan**). Do not call backend-specific APIs (raw GL calls, LWJGL OpenGL/Vulkan bindings) directly; go through Minecraft's / Fabric's rendering abstractions.
- Every feature must work **with and without shader mods such as Iris**. Do not rely on vanilla shader pipelines being active or unmodified; test both cases when touching rendering.

## Testing

- Unless a visible window is necessary (e.g. the user needs to see or interact with it), run dev clients for testing headlessly.
  `xvfb-run` alone is not enough on a Wayland desktop: Minecraft creates its window through SDL, which picks Wayland. Use
  `env -u WAYLAND_DISPLAY SDL_VIDEO_DRIVER=x11 xvfb-run -a -s "-screen 0 854x480x24" ./gradlew runClient` and check the log says `Created window using SDL video driver: x11`.

## Code style and structure

- Keep the codebase well organized, clean and neat.
- Functions are atomic: each does one thing, is small, and is named for what it does.
- Structure the code in clearly separated layers (e.g. core logic / data, game integration, client & rendering, mixins). Lower layers must not depend on higher ones; mixins stay thin and delegate to regular classes.
- Keep client-only code in client-only classes/entrypoints so the mod also loads on dedicated servers.

## Git workflow

- Commit and push whenever it is necessary or meaningful (a coherent, working unit of change). No approval needed.
- Write clear, focused commit messages.
