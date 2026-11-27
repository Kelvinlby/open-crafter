"""Side-by-side preview of an encoded episode: the RGB video, and the same video with its optical
flow drawn as half-transparent arrows coloured by direction.

    python -m opencrafter.flowviz <episode folder> <output.mp4> [--step 24] [--alpha 0.5]

Needs ffmpeg and ``pip install "opencrafter[flowviz]"`` (zstandard, OpenCV).

Flow of frame k is defined on frame k-1's pixel grid (see docs/ops/vision.md): an arrow starts at
a grid point p, where a surface was in the previous frame, and ends at p + flow, where that surface
is now. Points whose surface is occluded in frame k get no arrow. Hue is the direction (right =
red, down = yellow-green, left = cyan, up = violet); brightness is full for every drawn arrow.
"""

from __future__ import annotations

import argparse
import shutil
import subprocess
from pathlib import Path

import numpy as np

from opencrafter.episode import Episode


def _cv2():
    try:
        import cv2
    except ImportError as e:  # pragma: no cover - depends on the environment
        raise ImportError("flowviz needs OpenCV: pip install \"opencrafter[flowviz]\"") from e
    return cv2


def _direction_colours(dx: np.ndarray, dy: np.ndarray) -> np.ndarray:
    """RGB uint8 colour per arrow from its direction (HSV hue = angle, full saturation and value)."""
    cv2 = _cv2()
    hue = ((np.degrees(np.arctan2(dy, dx)) % 360.0) / 2.0).astype(np.uint8)  # OpenCV hue is 0-179
    hsv = np.stack([hue, np.full_like(hue, 255), np.full_like(hue, 255)], axis=-1)[None]
    return cv2.cvtColor(hsv, cv2.COLOR_HSV2RGB)[0]


def draw_arrows(rgb: np.ndarray, flow: np.ndarray, occluded: np.ndarray | None,
                step: int = 24, alpha: float = 0.5, min_length: float = 0.5) -> np.ndarray:
    """``rgb`` (H, W, 3) uint8 with flow (H, W, 2) drawn as arrows on a ``step``-pixel grid."""
    cv2 = _cv2()
    h, w = flow.shape[:2]
    ys, xs = np.mgrid[step // 2:h:step, step // 2:w:step]
    dx = flow[ys, xs, 0]
    dy = flow[ys, xs, 1]
    keep = np.hypot(dx, dy) >= min_length
    if occluded is not None:
        keep &= ~occluded[ys, xs, 0]
    xs, ys, dx, dy = xs[keep], ys[keep], dx[keep], dy[keep]
    if len(xs) == 0:  # nothing moves (or the first frame, which has no flow yet)
        return rgb.copy()
    colours = _direction_colours(dx, dy)
    overlay = rgb.copy()
    for x, y, u, v, colour in zip(xs, ys, dx, dy, colours):
        length = float(np.hypot(u, v))
        tip = min(0.4, 6.0 / max(length, 1e-3))
        cv2.arrowedLine(overlay, (int(x), int(y)), (int(round(x + u)), int(round(y + v))),
                        tuple(int(c) for c in colour), 2, cv2.LINE_AA, tipLength=tip)
    return cv2.addWeighted(overlay, alpha, rgb, 1.0 - alpha, 0.0)


def _label(image: np.ndarray, text: str) -> None:
    cv2 = _cv2()
    cv2.putText(image, text, (8, 20), cv2.FONT_HERSHEY_SIMPLEX, 0.55, (0, 0, 0), 3, cv2.LINE_AA)
    cv2.putText(image, text, (8, 20), cv2.FONT_HERSHEY_SIMPLEX, 0.55, (255, 255, 255), 1, cv2.LINE_AA)


def _video_encoder() -> list[str]:
    """H.264 for easy playback: on the GPU when NVENC works, else x264."""
    probe = subprocess.run(["ffmpeg", "-v", "error", "-f", "lavfi", "-i", "color=c=gray:s=256x256:d=0.1",
                            "-c:v", "h264_nvenc", "-f", "null", "-"], capture_output=True)
    if probe.returncode == 0:
        return ["-c:v", "h264_nvenc", "-rc", "vbr", "-cq", "21", "-b:v", "0", "-preset", "p5"]
    return ["-c:v", "libx264", "-crf", "20", "-preset", "fast"]


def render(episode_dir: str | Path, output: str | Path, step: int = 24, alpha: float = 0.5) -> int:
    """Writes the side-by-side video; returns the number of frames."""
    if shutil.which("ffmpeg") is None:
        raise RuntimeError("ffmpeg is needed")
    ep = Episode(episode_dir)
    flow = ep.stream("flow")
    occluded = ep.stream("flow_occluded") if (ep.path / "flow_occluded.ocs").exists() else None
    width, height = ep.info["config"]["width"], ep.info["config"]["height"]
    command = ["ffmpeg", "-v", "error", "-y", "-f", "rawvideo", "-pix_fmt", "rgb24", "-s", f"{width * 2}x{height}",
               "-framerate", str(ep.fps), "-i", "-",
               "-vf", "scale=out_color_matrix=bt709:out_range=tv,format=yuv420p",
               "-colorspace", "bt709", "-color_primaries", "bt709", "-color_trc", "bt709", "-color_range", "tv",
               *_video_encoder(), "-movflags", "+faststart", str(output)]
    frames = 0
    with subprocess.Popen(command, stdin=subprocess.PIPE) as ffmpeg:
        for index, rgb in enumerate(ep.rgb_frames()):
            if index >= len(flow):
                break
            left = rgb.copy()
            right = draw_arrows(rgb, flow[index], occluded[index] if occluded is not None else None, step, alpha)
            _label(left, "RGB")
            _label(right, "RGB + optical flow")
            ffmpeg.stdin.write(np.ascontiguousarray(np.hstack([left, right])).tobytes())
            frames += 1
        ffmpeg.stdin.close()
    if ffmpeg.returncode != 0:
        raise RuntimeError(f"ffmpeg failed with exit code {ffmpeg.returncode}")
    return frames


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("episode", help="episode folder written by ./gradlew encode (with the flow stream)")
    parser.add_argument("output", help="video file to write, e.g. preview.mp4")
    parser.add_argument("--step", type=int, default=24, help="grid spacing of the arrows in pixels (default 24)")
    parser.add_argument("--alpha", type=float, default=0.5, help="arrow opacity (default 0.5)")
    args = parser.parse_args()
    frames = render(args.episode, args.output, args.step, args.alpha)
    print(f"{args.output}: {frames} frames")


if __name__ == "__main__":
    main()
