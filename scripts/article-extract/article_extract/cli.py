"""
文章正文抽取 CLI：Trafilatura 拉取，或经 Lightpanda 渲染后再抽取。
stdout 输出一行 JSON，供 Java ProcessBuilder 解析。
"""

from __future__ import annotations

import argparse
import json
import os
import shutil
import subprocess
import sys


def configure_stdio_utf8() -> None:
    """避免 Windows 默认 GBK 导致含特殊字符的结果无法写出。"""
    os.environ.setdefault("PYTHONIOENCODING", "utf-8")
    os.environ.setdefault("PYTHONUTF8", "1")
    for stream in (sys.stdout, sys.stderr):
        try:
            stream.reconfigure(encoding="utf-8", errors="replace")  # type: ignore[attr-defined]
        except Exception:  # noqa: BLE001
            pass


def emit(payload: dict) -> int:
    """打印一行 JSON 并返回进程码。"""
    line = json.dumps(payload, ensure_ascii=True) + "\n"
    try:
        sys.stdout.write(line)
        sys.stdout.flush()
    except UnicodeEncodeError:
        sys.stdout.buffer.write(line.encode("utf-8", errors="replace"))
        sys.stdout.buffer.flush()
    return 0 if payload.get("ok") else 1


def _to_date_input_text(html: str, max_chars: int = 12000) -> str:
    """去掉 style、保留 script 文本、剥标签，供发布日抽取。"""
    import re

    if not html:
        return ""
    text = re.sub(r"(?is)<style\b[^>]*>.*?</style>", " ", html)
    text = re.sub(r"(?is)<script\b[^>]*>", " ", text)
    text = re.sub(r"(?is)</script>", " ", text)
    text = re.sub(r"(?is)<[^>]+>", " ", text)
    text = re.sub(r"\s+", " ", text).strip()
    if len(text) > max_chars:
        return text[:max_chars]
    return text


def _extract_from_html(html: str, url: str) -> dict:
    """对已有 HTML 做 Trafilatura 抽取（不再次联网）。"""
    try:
        import trafilatura
    except ImportError as exc:  # noqa: BLE001
        return {
            "ok": False,
            "text": "",
            "date": "",
            "dateInputText": "",
            "error": f"未安装 trafilatura: {exc}",
        }

    date_input_text = _to_date_input_text(html)
    text = trafilatura.extract(
        html,
        include_comments=False,
        include_tables=False,
        favor_recall=True,
        url=url,
    )
    date = ""
    try:
        meta = trafilatura.extract_metadata(html, default_url=url)
        if meta is not None and getattr(meta, "date", None):
            date = str(meta.date)
    except Exception:  # noqa: BLE001
        date = ""
    text = (text or "").strip()
    if len(text) < 80:
        # 抽取失败时回退剥标签文本（Lightpanda 已执行 JS）
        fallback = date_input_text.strip()
        if len(fallback) >= 80:
            return {
                "ok": True,
                "text": fallback[:20000],
                "date": date,
                "dateInputText": date_input_text,
                "error": "",
            }
        return {
            "ok": False,
            "text": text,
            "date": date,
            "dateInputText": date_input_text,
            "error": f"正文过短（{len(text)} 字）",
        }
    return {
        "ok": True,
        "text": text,
        "date": date,
        "dateInputText": date_input_text,
        "error": "",
    }


def extract_url(url: str) -> dict:
    """HTTP 下载并抽取正文。"""
    try:
        import trafilatura
    except ImportError as exc:  # noqa: BLE001
        return {
            "ok": False,
            "text": "",
            "date": "",
            "dateInputText": "",
            "error": f"未安装 trafilatura: {exc}",
        }

    try:
        downloaded = trafilatura.fetch_url(url)
        if not downloaded:
            return {
                "ok": False,
                "text": "",
                "date": "",
                "dateInputText": "",
                "error": "下载失败或返回空",
            }
        return _extract_from_html(downloaded, url)
    except Exception as exc:  # noqa: BLE001
        return {
            "ok": False,
            "text": "",
            "date": "",
            "dateInputText": "",
            "error": str(exc),
        }


def _build_lightpanda_command(
    url: str,
    *,
    mode: str,
    docker_container: str,
    executable: str,
    dump: str = "markdown",
    strip_mode: str | None = "clutter",
    as_json: bool = True,
) -> list[str]:
    """组装 lightpanda fetch 命令。"""
    fetch_args = [
        "fetch",
        "--obey-robots",
        "--dump",
        dump,
        "--wait-ms",
        "8000",
    ]
    if strip_mode:
        fetch_args.extend(["--strip-mode", strip_mode])
    if as_json:
        fetch_args.append("--json")
    fetch_args.append(url)
    normalized = (mode or "docker-exec").strip().lower()
    if normalized == "binary":
        binary = executable.strip() if executable else "lightpanda"
        if binary == "lightpanda" and shutil.which(binary) is None:
            raise FileNotFoundError(
                "未找到 lightpanda 可执行文件；请安装或改用 docker-exec 模式"
            )
        return [binary, *fetch_args]
    container = (docker_container or "chengwenfang-lightpanda").strip()
    docker_bin = shutil.which("docker")
    if not docker_bin:
        raise FileNotFoundError("未找到 docker；请安装 Docker 或改用 binary 模式")
    return [docker_bin, "exec", container, "lightpanda", *fetch_args]


def _run_lightpanda(
    command: list[str],
    *,
    timeout_seconds: float,
) -> tuple[int, str, str]:
    """执行 Lightpanda，返回 (code, stdout, stderr)。"""
    completed = subprocess.run(
        command,
        capture_output=True,
        timeout=max(1.0, float(timeout_seconds)),
        check=False,
    )
    stdout = (completed.stdout or b"").decode("utf-8", errors="replace")
    stderr = (completed.stderr or b"").decode("utf-8", errors="replace").strip()
    return completed.returncode, stdout, stderr


def lightpanda_extract_url(
    url: str,
    *,
    mode: str = "docker-exec",
    docker_container: str = "chengwenfang-lightpanda",
    executable: str = "lightpanda",
    timeout_seconds: float = 45.0,
) -> dict:
    """经 Lightpanda 渲染：优先 clutter Markdown，失败再 HTML + Trafilatura。"""
    try:
        md_command = _build_lightpanda_command(
            url,
            mode=mode,
            docker_container=docker_container,
            executable=executable,
            dump="markdown",
            strip_mode="clutter",
            as_json=True,
        )
    except FileNotFoundError as exc:
        return {
            "ok": False,
            "text": "",
            "date": "",
            "dateInputText": "",
            "error": str(exc),
        }

    try:
        code, stdout, stderr = _run_lightpanda(md_command, timeout_seconds=timeout_seconds)
    except subprocess.TimeoutExpired:
        return {
            "ok": False,
            "text": "",
            "date": "",
            "dateInputText": "",
            "error": f"Lightpanda 超时（{int(timeout_seconds)}s）",
        }
    except Exception as exc:  # noqa: BLE001
        return {
            "ok": False,
            "text": "",
            "date": "",
            "dateInputText": "",
            "error": str(exc),
        }

    md_text = ""
    if code == 0 and stdout.strip():
        try:
            payload = json.loads(stdout.strip().splitlines()[-1])
            if isinstance(payload, dict) and not payload.get("error"):
                md_text = str(payload.get("content") or "").strip()
        except json.JSONDecodeError:
            md_text = ""
    if len(md_text) >= 80:
        clipped = md_text[:20000]
        return {
            "ok": True,
            "text": clipped,
            "date": "",
            "dateInputText": clipped[:12000],
            "error": "",
        }

    # clutter Markdown 不够时，回退 HTML + Trafilatura
    try:
        html_command = _build_lightpanda_command(
            url,
            mode=mode,
            docker_container=docker_container,
            executable=executable,
            dump="html",
            strip_mode=None,
            as_json=False,
        )
        code, stdout, stderr = _run_lightpanda(html_command, timeout_seconds=timeout_seconds)
    except subprocess.TimeoutExpired:
        return {
            "ok": False,
            "text": "",
            "date": "",
            "dateInputText": "",
            "error": f"Lightpanda 超时（{int(timeout_seconds)}s）",
        }
    except Exception as exc:  # noqa: BLE001
        return {
            "ok": False,
            "text": "",
            "date": "",
            "dateInputText": "",
            "error": str(exc),
        }

    if code != 0:
        detail = stderr or stdout.strip() or f"exit={code}"
        return {
            "ok": False,
            "text": "",
            "date": "",
            "dateInputText": "",
            "error": f"Lightpanda 失败: {detail[:400]}",
        }

    html = stdout.strip()
    if not html:
        return {
            "ok": False,
            "text": "",
            "date": "",
            "dateInputText": "",
            "error": "Lightpanda 返回空 HTML",
        }
    return _extract_from_html(html, url)


def main(argv: list[str] | None = None) -> int:
    """CLI 入口。"""
    configure_stdio_utf8()
    parser = argparse.ArgumentParser(description="文章正文抽取（Trafilatura / Lightpanda）")
    parser.add_argument("--url", required=True, help="文章 URL")
    parser.add_argument(
        "--via",
        choices=("trafilatura", "lightpanda"),
        default="trafilatura",
        help="抓取路径：默认 HTTP Trafilatura；lightpanda 为浏览器渲染后抽取",
    )
    parser.add_argument(
        "--browser-mode",
        default="docker-exec",
        choices=("docker-exec", "binary"),
        help="Lightpanda 调用方式",
    )
    parser.add_argument(
        "--docker-container",
        default="chengwenfang-lightpanda",
        help="docker-exec 容器名",
    )
    parser.add_argument(
        "--lightpanda-bin",
        default="lightpanda",
        help="binary 模式可执行文件",
    )
    parser.add_argument(
        "--timeout-seconds",
        type=float,
        default=45.0,
        help="Lightpanda 超时秒数",
    )
    args = parser.parse_args(argv)
    url = args.url.strip()
    if args.via == "lightpanda":
        return emit(
            lightpanda_extract_url(
                url,
                mode=args.browser_mode,
                docker_container=args.docker_container,
                executable=args.lightpanda_bin,
                timeout_seconds=args.timeout_seconds,
            )
        )
    return emit(extract_url(url))


if __name__ == "__main__":
    raise SystemExit(main())
