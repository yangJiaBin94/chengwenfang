"""Decode a Cursor browser CDP Page.captureScreenshot JSON dump to PNG."""

from __future__ import annotations

import base64
import io
import json
import sys
from pathlib import Path

from PIL import Image


def find_b64(obj: object) -> str | None:
    if isinstance(obj, dict):
        data = obj.get("data")
        if isinstance(data, str) and len(data) > 1000:
            return data
        for value in obj.values():
            found = find_b64(value)
            if found:
                return found
    return None


def main() -> None:
    if len(sys.argv) != 3:
        raise SystemExit("usage: save-cdp-screenshot.py <cdp-json> <out.png>")
    src = Path(sys.argv[1])
    dest = Path(sys.argv[2])
    payload = json.loads(src.read_text(encoding="utf-8"))
    b64 = find_b64(payload)
    if not b64:
        raise SystemExit(f"no screenshot data in {src}")
    image = Image.open(io.BytesIO(base64.b64decode(b64))).convert("RGB")
    dest.parent.mkdir(parents=True, exist_ok=True)
    image.save(dest, "PNG", optimize=True)
    print(f"{dest.name}: {image.size}")


if __name__ == "__main__":
    main()
