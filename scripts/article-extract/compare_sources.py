#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
对比直连剥文 / Jina / Trafilatura / Lightpanda 成功率（只读抽样，不进业务流水线）。

用法：
  python scripts/article-extract/compare_sources.py --csv .workbench/reports/xxx.csv --limit 20
  # 需先：docker compose --profile lightpanda up -d
  python scripts/article-extract/compare_sources.py --csv ... --limit 10
"""

from __future__ import annotations

import argparse
import csv
import json
import re
import sys
import time
import urllib.request
from collections import Counter
from pathlib import Path
from urllib.parse import urlparse

MIN_CHARS = 80
UA = (
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
    "(KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"
)


def direct_strip(url: str, timeout: float = 12.0) -> tuple[bool, int, str]:
    try:
        req = urllib.request.Request(url, headers={"User-Agent": UA})
        with urllib.request.urlopen(req, timeout=timeout) as resp:
            html = resp.read().decode("utf-8", errors="replace")
        text = re.sub(r"(?is)<script[^>]*>.*?</script>", " ", html)
        text = re.sub(r"(?is)<style[^>]*>.*?</style>", " ", text)
        text = re.sub(r"(?is)<[^>]+>", " ", text)
        text = re.sub(r"\s+", " ", text).strip()
        return len(text) >= MIN_CHARS, len(text), ""
    except Exception as exc:  # noqa: BLE001
        return False, 0, str(exc)


def jina_fetch(url: str, timeout: float = 12.0) -> tuple[bool, int, str]:
    try:
        target = "https://r.jina.ai/" + url
        req = urllib.request.Request(
            target,
            headers={"User-Agent": UA, "Accept": "text/plain,text/markdown,*/*"},
        )
        with urllib.request.urlopen(req, timeout=timeout) as resp:
            text = resp.read().decode("utf-8", errors="replace").strip()
        return len(text) >= MIN_CHARS, len(text), ""
    except Exception as exc:  # noqa: BLE001
        return False, 0, str(exc)


def trafilatura_fetch(url: str) -> tuple[bool, int, str]:
    try:
        from article_extract.cli import extract_url

        payload = extract_url(url)
        text = (payload.get("text") or "").strip()
        ok = bool(payload.get("ok")) and len(text) >= MIN_CHARS
        return ok, len(text), payload.get("error") or ""
    except Exception as exc:  # noqa: BLE001
        return False, 0, str(exc)


def lightpanda_fetch(
    url: str,
    *,
    mode: str,
    docker_container: str,
    timeout_seconds: float,
) -> tuple[bool, int, str]:
    try:
        from article_extract.cli import lightpanda_extract_url

        payload = lightpanda_extract_url(
            url,
            mode=mode,
            docker_container=docker_container,
            timeout_seconds=timeout_seconds,
        )
        text = (payload.get("text") or "").strip()
        ok = bool(payload.get("ok")) and len(text) >= MIN_CHARS
        return ok, len(text), payload.get("error") or ""
    except Exception as exc:  # noqa: BLE001
        return False, 0, str(exc)


def load_article_urls(csv_path: Path, limit: int) -> list[str]:
    rows = list(csv.DictReader(csv_path.open(encoding="utf-8-sig")))
    urls: list[str] = []
    seen: set[str] = set()
    for row in rows:
        if row.get("过滤阶段") != "保留（通过获取阶段）":
            continue
        if row.get("类型") != "文章":
            continue
        url = (row.get("链接") or "").strip()
        if not url or url in seen:
            continue
        seen.add(url)
        urls.append(url)
        if len(urls) >= limit:
            break
    return urls


def main() -> int:
    parser = argparse.ArgumentParser(description="文章正文来源对比")
    parser.add_argument("--csv", required=True)
    parser.add_argument("--limit", type=int, default=20)
    parser.add_argument("--pause", type=float, default=0.4)
    parser.add_argument("--skip-trafilatura", action="store_true")
    parser.add_argument("--skip-jina", action="store_true", help="当前网络访问不到 Jina 时跳过")
    parser.add_argument(
        "--skip-lightpanda",
        action="store_true",
        help="未启动 Lightpanda 容器时跳过",
    )
    parser.add_argument(
        "--browser-mode",
        default="docker-exec",
        choices=("docker-exec", "binary"),
    )
    parser.add_argument("--docker-container", default="workbench-lightpanda")
    parser.add_argument("--lightpanda-timeout", type=float, default=45.0)
    args = parser.parse_args()
    csv_path = Path(args.csv)
    if not csv_path.is_absolute():
        csv_path = Path.cwd() / csv_path
    urls = load_article_urls(csv_path, args.limit)
    if not urls:
        print("未找到通过获取阶段的文章链接", flush=True)
        return 1

    print(f"抽样文章 URL 数={len(urls)}", flush=True)
    scores = Counter()
    for index, url in enumerate(urls, start=1):
        host = urlparse(url).hostname or ""
        print(f"[{index}/{len(urls)}] {host}", flush=True)
        ok_d, len_d, err_d = direct_strip(url)
        scores["direct_ok"] += int(ok_d)
        line = f"  直连={'OK' if ok_d else 'NO'}({len_d})"
        if not args.skip_jina:
            ok_j, len_j, err_j = jina_fetch(url)
            scores["jina_ok"] += int(ok_j)
            line += f" Jina={'OK' if ok_j else 'NO'}({len_j})"
            if not ok_j and err_j:
                line += f" jinaErr={err_j[:40]}"
        if not args.skip_trafilatura:
            ok_t, len_t, err_t = trafilatura_fetch(url)
            scores["trafilatura_ok"] += int(ok_t)
            line += f" Trafilatura={'OK' if ok_t else 'NO'}({len_t})"
            if not ok_t and err_t:
                line += f" [{err_t[:60]}]"
        if not args.skip_lightpanda:
            ok_l, len_l, err_l = lightpanda_fetch(
                url,
                mode=args.browser_mode,
                docker_container=args.docker_container,
                timeout_seconds=args.lightpanda_timeout,
            )
            scores["lightpanda_ok"] += int(ok_l)
            line += f" Lightpanda={'OK' if ok_l else 'NO'}({len_l})"
            if not ok_l and err_l:
                line += f" lpErr={err_l[:60]}"
        if not ok_d and err_d:
            line += f" directErr={err_d[:40]}"
        print(line, flush=True)
        if args.pause > 0 and index < len(urls):
            time.sleep(args.pause)

    total = len(urls)
    print("\n===== 成功率 =====", flush=True)
    print(f"直连剥文: {scores['direct_ok']}/{total}", flush=True)
    if not args.skip_jina:
        print(f"Jina: {scores['jina_ok']}/{total}", flush=True)
    if not args.skip_trafilatura:
        print(f"Trafilatura: {scores['trafilatura_ok']}/{total}", flush=True)
    if not args.skip_lightpanda:
        print(f"Lightpanda: {scores['lightpanda_ok']}/{total}", flush=True)
    out = {
        "total": total,
        "direct_ok": scores["direct_ok"],
        "jina_ok": scores["jina_ok"] if not args.skip_jina else None,
        "trafilatura_ok": scores.get("trafilatura_ok", 0) if not args.skip_trafilatura else None,
        "lightpanda_ok": scores.get("lightpanda_ok", 0) if not args.skip_lightpanda else None,
        "skip_jina": bool(args.skip_jina),
        "skip_lightpanda": bool(args.skip_lightpanda),
    }
    print(json.dumps(out, ensure_ascii=False), flush=True)
    return 0


if __name__ == "__main__":
    # 保证可 import article_extract
    root = Path(__file__).resolve().parent
    if str(root) not in sys.path:
        sys.path.insert(0, str(root))
    raise SystemExit(main())
