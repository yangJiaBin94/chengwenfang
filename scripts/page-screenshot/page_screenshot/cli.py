"""打开 URL 截 PNG：分段滚屏、定位文本一屏，或 PDF 首页；stdout 输出 JSON。"""

from __future__ import annotations

import argparse
import json
import re
import sys
import urllib.request
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


def looks_like_pdf_url(url: str) -> bool:
    """URL 路径是否像 PDF。"""
    path = (url or "").split("?", 1)[0].split("#", 1)[0].lower()
    return path.endswith(".pdf")


# PDF 下载硬上限（字节），避免超大文件撑爆 CLI 内存。
MAX_PDF_BYTES = 25 * 1024 * 1024


def download_bytes(url: str, timeout_seconds: float, max_bytes: int = MAX_PDF_BYTES) -> tuple[bytes, str]:
    """下载 URL；超过 max_bytes 则抛错。返回 (bytes, content-type)。"""
    request = urllib.request.Request(
        url,
        headers={
            "User-Agent": "chengwenfang-page-screenshot/1.0",
            "Accept": "*/*",
        },
        method="GET",
    )
    with urllib.request.urlopen(request, timeout=max(5.0, float(timeout_seconds))) as response:
        content_type = str(response.headers.get("Content-Type") or "").lower()
        content_length = response.headers.get("Content-Length")
        if content_length is not None:
            try:
                declared = int(content_length)
            except (TypeError, ValueError):
                declared = -1
            if declared > max_bytes:
                raise ValueError(f"PDF 过大（Content-Length={content_length} > {max_bytes}）")
        chunks: list[bytes] = []
        total = 0
        while True:
            chunk = response.read(1024 * 256)
            if not chunk:
                break
            total += len(chunk)
            if total > max_bytes:
                raise ValueError(f"PDF 过大（已读 {total} > {max_bytes}）")
            chunks.append(chunk)
        return b"".join(chunks), content_type


def capture_pdf_first_page(
    url: str,
    out_dir: Path,
    *,
    timeout_seconds: float = 45.0,
) -> dict:
    """下载 PDF 并渲染第 1 页为 PNG。"""
    try:
        import fitz  # PyMuPDF
    except ImportError:
        return {
            "ok": False,
            "paths": [],
            "error": "未安装 pymupdf，请在 scripts/page-screenshot venv 中 pip install -r requirements.txt",
        }

    out_dir.mkdir(parents=True, exist_ok=True)
    try:
        data, content_type = download_bytes(url, timeout_seconds)
    except Exception as exc:  # noqa: BLE001
        return {"ok": False, "paths": [], "error": f"下载 PDF 失败: {str(exc)[:300]}"}

    if not data:
        return {"ok": False, "paths": [], "error": "PDF 内容为空"}
    if "html" in content_type and not looks_like_pdf_url(url):
        return {"ok": False, "paths": [], "error": f"响应不是 PDF: {content_type[:80]}"}

    try:
        document = fitz.open(stream=data, filetype="pdf")
        try:
            if document.page_count < 1:
                return {"ok": False, "paths": [], "error": "PDF 无页面"}
            page = document.load_page(0)
            # 约 144 DPI，兼顾清晰与体积
            pixmap = page.get_pixmap(matrix=fitz.Matrix(2.0, 2.0), alpha=False)
            target = out_dir / "shot-01.png"
            pixmap.save(str(target))
            return {"ok": True, "paths": [str(target.resolve())], "error": ""}
        finally:
            document.close()
    except Exception as exc:  # noqa: BLE001
        return {"ok": False, "paths": [], "error": f"渲染 PDF 失败: {str(exc)[:300]}"}


def should_treat_as_pdf(url: str, timeout_seconds: float) -> bool:
    """URL 或 HEAD Content-Type 判定是否走 PDF 路径。"""
    if looks_like_pdf_url(url):
        return True
    try:
        request = urllib.request.Request(
            url,
            headers={"User-Agent": "chengwenfang-page-screenshot/1.0"},
            method="HEAD",
        )
        with urllib.request.urlopen(request, timeout=min(15.0, max(5.0, float(timeout_seconds)))) as response:
            content_type = str(response.headers.get("Content-Type") or "").lower()
            return "application/pdf" in content_type
    except Exception:  # noqa: BLE001
        return False


def _normalize_find_hint(text: str) -> str:
    """弯引号与空白归一，便于 DOM 子串匹配。"""
    hint = (text or "").strip()
    if not hint:
        return ""
    hint = (
        hint.replace("\u201c", '"')
        .replace("\u201d", '"')
        .replace("\u2018", "'")
        .replace("\u2019", "'")
        .replace("\u3000", " ")
    )
    return " ".join(hint.split())


def _find_text_candidates(find_text: str) -> list[str]:
    """生成定位候选：整句 → 分号拆段 → 短前缀。"""
    hint = _normalize_find_hint(find_text)
    if not hint:
        return []
    if len(hint) > 80:
        hint = hint[:80]
    candidates: list[str] = []
    seen: set[str] = set()

    def add(value: str) -> None:
        item = _normalize_find_hint(value)
        if len(item) < 4 or item in seen:
            return
        seen.add(item)
        candidates.append(item)

    add(hint)
    for part in re.split(r"[；;。！？!?\n]+", hint):
        add(part)
    if len(hint) > 12:
        add(hint[: max(8, len(hint) // 2)])
    return candidates


def _scroll_to_needle(page, needle: str) -> bool:
    """将含 needle 的节点滚入视口。"""
    try:
        locator = page.get_by_text(needle, exact=False)
        count = locator.count()
        if count <= 0:
            escaped = json.dumps(needle)
            found = page.evaluate(
                f"""() => {{
                  const needle = {escaped};
                  const walker = document.createTreeWalker(document.body, NodeFilter.SHOW_TEXT);
                  while (walker.nextNode()) {{
                    const node = walker.currentNode;
                    if (node && node.textContent && node.textContent.includes(needle)) {{
                      const el = node.parentElement;
                      if (el) {{
                        el.scrollIntoView({{block: 'center', inline: 'nearest'}});
                        return true;
                      }}
                    }}
                  }}
                  return false;
                }}"""
            )
            return bool(found)
        locator.first.scroll_into_view_if_needed(timeout=5000)
        page.wait_for_timeout(300)
        return True
    except Exception:  # noqa: BLE001
        return False


def scroll_to_find_text(page, find_text: str) -> bool:
    """将含 find_text 的节点滚入视口；找不到返回 False。"""
    for candidate in _find_text_candidates(find_text):
        if _scroll_to_needle(page, candidate):
            return True
    return False


def capture(
    url: str,
    out_dir: Path,
    *,
    max_shots: int = 3,
    width: int = 1280,
    height: int = 900,
    timeout_seconds: float = 45.0,
    find_text: str = "",
) -> dict:
    """截图；PDF 走首页渲染，网页可按 find_text 定位后再截。"""
    if should_treat_as_pdf(url, timeout_seconds):
        return capture_pdf_first_page(url, out_dir, timeout_seconds=timeout_seconds)

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
    # 有定位文本时默认只截对准后的一屏
    effective_max = 1 if (find_text or "").strip() else max(1, int(max_shots))

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

                hint = (find_text or "").strip()
                # 成文佐证：要求定位时找不到则失败，避免把无关首屏当硬证据
                if hint and not scroll_to_find_text(page, hint):
                    return {
                        "ok": False,
                        "paths": [],
                        "error": f"未在页面定位到文本，已放弃截屏以免假佐证: {hint[:80]}",
                    }

                for index in range(effective_max):
                    target = out_dir / f"shot-{index + 1:02d}.png"
                    page.screenshot(path=str(target), full_page=False, type="png")
                    paths.append(str(target.resolve()))
                    if index + 1 >= effective_max:
                        break
                    before = page.evaluate("() => window.scrollY")
                    page.evaluate(f"() => window.scrollBy(0, {height})")
                    page.wait_for_timeout(400)
                    after = page.evaluate("() => window.scrollY")
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
    parser = argparse.ArgumentParser(description="网页/PDF 截图（Vision 兜底与成文佐证）")
    parser.add_argument("--url", required=True, help="页面或 PDF URL")
    parser.add_argument("--out-dir", required=True, help="PNG 输出目录")
    parser.add_argument("--max-shots", type=int, default=3, help="最多截屏张数（有 --find-text 时固定 1）")
    parser.add_argument("--viewport", default="1280x900", help="视口宽x高")
    parser.add_argument("--timeout-seconds", type=float, default=45.0, help="导航/下载超时秒数")
    parser.add_argument(
        "--find-text",
        default="",
        help="可选：将页面滚到含该文本的位置后再截一屏",
    )
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
        find_text=str(args.find_text or ""),
    )
    emit(result)
    return 0 if result.get("ok") else 1


if __name__ == "__main__":
    raise SystemExit(main())
