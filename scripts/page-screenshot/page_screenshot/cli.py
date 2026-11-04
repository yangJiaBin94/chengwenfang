"""打开 URL，自上而下滚屏截最多 N 张 PNG，stdout 输出 JSON。"""

from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path


def configure_stdio_utf8() -> None:
    """Windows 控制台尽量 UTF-8。"""
    for stream in (sys.stdout, sys.stderr):
        try:
            stream.reconfigure(encoding="utf-8")  # type: ignore[attr-defined]
        except Exception:  # noqa: BLE001
            pass


def emit(payload: dict) -> None:
    """打印一行 JSON。"""
    print(json.dumps(payload, ensure_ascii=False), flush=True)


def capture(
    url: str,
    out_dir: Path,
    *,
    max_shots: int = 3,
    width: int = 1280,
    height: int = 900,
    timeout_seconds: float = 45.0,
) -> dict:
    """分段截图；成功返回 ok 与 paths。"""
    try:
        from playwright.sync_api import sync_playwright
    except ImportError:
        return {
            "ok": False,
            "paths": [],
            "error": "未安装 playwright，请在 scripts/page-screenshot venv 中 pip install -r requirements.txt",
        }

    out_dir.mkdir(parents=True, exist_ok=True)
    paths: list[str] = []
    timeout_ms = int(max(1.0, float(timeout_seconds)) * 1000)

    try:
        with sync_playwright() as playwright:
            browser = playwright.chromium.launch(headless=True)
            try:
                page = browser.new_page(viewport={"width": width, "height": height})
                page.set_default_timeout(timeout_ms)
                page.goto(url, wait_until="domcontentloaded")
                try:
                    page.wait_for_load_state("networkidle", timeout=min(8000, timeout_ms))
                except Exception:  # noqa: BLE001
                    pass
                page.wait_for_timeout(500)

                for index in range(max(1, int(max_shots))):
                    target = out_dir / f"shot-{index + 1:02d}.png"
                    page.screenshot(path=str(target), full_page=False, type="png")
                    paths.append(str(target.resolve()))
                    if index + 1 >= max_shots:
                        break
                    before = page.evaluate("() => window.scrollY")
                    page.evaluate(f"() => window.scrollBy(0, {height})")
                    page.wait_for_timeout(400)
                    after = page.evaluate("() => window.scrollY")
                    # 滚不动说明已到底
                    if after <= before:
                        break
            finally:
                browser.close()
    except Exception as exc:  # noqa: BLE001
        return {"ok": False, "paths": paths, "error": str(exc)[:400]}

    if not paths:
        return {"ok": False, "paths": [], "error": "未生成截图"}
    return {"ok": True, "paths": paths, "error": ""}


def main(argv: list[str] | None = None) -> int:
    """CLI 入口。"""
    configure_stdio_utf8()
    parser = argparse.ArgumentParser(description="网页分段截图（Vision 兜底）")
    parser.add_argument("--url", required=True, help="页面 URL")
    parser.add_argument("--out-dir", required=True, help="PNG 输出目录")
    parser.add_argument("--max-shots", type=int, default=3, help="最多截屏张数")
    parser.add_argument("--viewport", default="1280x900", help="视口宽x高")
    parser.add_argument("--timeout-seconds", type=float, default=45.0, help="导航超时秒数")
    args = parser.parse_args(argv)

    width, height = 1280, 900
    try:
        parts = str(args.viewport).lower().split("x")
        if len(parts) == 2:
            width = max(320, int(parts[0]))
            height = max(240, int(parts[1]))
    except ValueError:
        pass

    result = capture(
        args.url.strip(),
        Path(args.out_dir),
        max_shots=max(1, int(args.max_shots)),
        width=width,
        height=height,
        timeout_seconds=float(args.timeout_seconds),
    )
    emit(result)
    return 0 if result.get("ok") else 1


if __name__ == "__main__":
    raise SystemExit(main())
