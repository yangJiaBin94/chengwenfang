"""Normalize README screenshots to identical canvas size (never crop content)."""

from __future__ import annotations

from pathlib import Path

from PIL import Image

RAW = Path(r"C:\Users\嘉彬\AppData\Local\Temp\cursor\screenshots\_raw")
OUT = Path("docs/screenshots")
# Fixed display canvas for all stills (matches capture viewport)
W, H = 1440, 900
BG = (247, 248, 246)


def contain_canvas(im: Image.Image, width: int = W, height: int = H) -> Image.Image:
    """Scale to fit inside canvas; pad with background. Never crop."""
    rgb = im.convert("RGB")
    if rgb.size == (width, height):
        return rgb
    scale = min(width / rgb.width, height / rgb.height)
    nw = max(1, int(round(rgb.width * scale)))
    nh = max(1, int(round(rgb.height * scale)))
    resized = rgb.resize((nw, nh), Image.Resampling.LANCZOS)
    canvas = Image.new("RGB", (width, height), BG)
    left = (width - nw) // 2
    top = (height - nh) // 2
    canvas.paste(resized, (left, top))
    return canvas


def save_png(src_name: str, dst_name: str) -> None:
    src = RAW / src_name
    if not src.exists():
        print(f"missing {src}")
        return
    out = contain_canvas(Image.open(src))
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
        # Same aspect as stills — avoid a second crop pass
        frames.append(contain_canvas(Image.open(path), W, H))
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
