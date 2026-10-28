"""Trim letterbox / near-uniform edge padding from README screenshots."""

from __future__ import annotations

from pathlib import Path

from PIL import Image

PAD = (244, 246, 245)
TOL = 10
DIR = Path("docs/screenshots")
MAX_W = 1600


def near(pixel: tuple[int, ...], target: tuple[int, int, int] = PAD, tol: int = TOL) -> bool:
    return all(abs(int(a) - int(b)) <= tol for a, b in zip(pixel[:3], target))


def content_bbox(im: Image.Image) -> tuple[int, int, int, int]:
    rgb = im.convert("RGB")
    width, height = rgb.size
    pixels = rgb.load()
    left, top, right, bottom = width, height, 0, 0
    found = False
    for y in range(height):
        for x in range(width):
            if not near(pixels[x, y]):
                found = True
                left = min(left, x)
                top = min(top, y)
                right = max(right, x)
                bottom = max(bottom, y)
    if not found:
        return (0, 0, width, height)
    return (left, top, right + 1, bottom + 1)


def trim_file(path: Path) -> None:
    original = Image.open(path).convert("RGBA")
    box = content_bbox(original)
    cropped = original.crop(box)
    if cropped.width > MAX_W:
        ratio = MAX_W / cropped.width
        cropped = cropped.resize(
            (MAX_W, max(1, int(cropped.height * ratio))),
            Image.Resampling.LANCZOS,
        )
    cropped.convert("RGB").save(path, "PNG", optimize=True)
    print(f"{path.name}: {original.size} crop={box} -> {cropped.size}")


def main() -> None:
    names = [
        "01-course-mode.png",
        "02-hotnews-mode.png",
        "03-workflows.png",
        "04-settings-ai.png",
        "05-hotnews-radar.png",
    ]
    for name in names:
        path = DIR / name
        if path.exists():
            trim_file(path)


if __name__ == "__main__":
    main()
