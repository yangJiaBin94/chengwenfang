"""Normalize README screenshots to identical canvas size (product viewport only)."""

from __future__ import annotations

import shutil
from pathlib import Path

from PIL import Image, ImageSequence

RAW = Path(r"C:\Users\嘉彬\AppData\Local\Temp\cursor\screenshots\_raw")
OUT = Path("docs/screenshots")
# Fixed display canvas for all stills (matches capture viewport)
W, H = 1440, 900
BG = (247, 248, 246)


def fit_canvas(im: Image.Image, width: int = W, height: int = H) -> Image.Image:
    rgb = im.convert("RGB")
    # Already exact? keep
    if rgb.size == (width, height):
        return rgb
    # Cover-fit into canvas then center-crop (no letterbox bars)
    scale = max(width / rgb.width, height / rgb.height)
    nw, nh = max(1, int(rgb.width * scale)), max(1, int(rgb.height * scale))
    resized = rgb.resize((nw, nh), Image.Resampling.LANCZOS)
    left = max(0, (nw - width) // 2)
    top = max(0, (nh - height) // 2)
    return resized.crop((left, top, left + width, top + height))


def save_png(src_name: str, dst_name: str) -> None:
    src = RAW / src_name
    if not src.exists():
        print(f"missing {src}")
        return
    out = fit_canvas(Image.open(src))
    dest = OUT / dst_name
    out.save(dest, "PNG", optimize=True)
    print(f"{dst_name}: {out.size} ({dest.stat().st_size // 1024} KB)")


def build_gif() -> None:
    frames_src = [
        RAW / "02-hotnews-mode.png",
        RAW / "05-hotnews-radar.png",
        RAW / "gif-sources.png",
    ]
    frames: list[Image.Image] = []
    for path in frames_src:
        if not path.exists():
            print(f"gif skip missing {path.name}")
            return
        frames.append(fit_canvas(Image.open(path), 1200, 750))
    dest = OUT / "hotnews-walkthrough.gif"
    frames[0].save(
        dest,
        save_all=True,
        append_images=frames[1:],
        duration=1800,
        loop=0,
        optimize=False,
    )
    print(f"hotnews-walkthrough.gif: {frames[0].size} ({dest.stat().st_size // 1024} KB)")


def main() -> None:
    OUT.mkdir(parents=True, exist_ok=True)
    mapping = {
        "01-course-mode.png": "01-course-mode.png",
        "02-hotnews-mode.png": "02-hotnews-mode.png",
        "03-workflows.png": "03-workflows.png",
        "04-settings-ai.png": "04-settings-ai.png",
        "05-hotnews-radar.png": "05-hotnews-radar.png",
        "06-course-route.png": "06-course-route.png",
    }
    for src, dst in mapping.items():
        save_png(src, dst)
    build_gif()


if __name__ == "__main__":
    main()
