#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
一次性 SearXNG 采集抽样报告（不进入业务流水线）。

用法：
  python scripts/searxng-collect-report.py
  python scripts/searxng-collect-report.py --base http://127.0.0.1:8080 --out .chengwenfang/reports

默认使用「中老年身心健康」主题当前 SearXNG 种子词；可用 --queries-file 覆盖。
输出：HTML 静态报告 + CSV（Excel 可直接打开）。
"""

from __future__ import annotations

import argparse
import csv
import html
import json
import re
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
from collections import Counter, defaultdict
from datetime import datetime
from pathlib import Path
from urllib.parse import urlparse

DEFAULT_QUERIES = [
    "中老年科学养生方法",
    "中老年养生常见误区",
    "中老年器官调养技巧",
    "中老年饮食营养指南",
    "中老年运动安全注意事项",
    "中老年睡眠障碍改善",
    "中老年心理健康维护",
    "中老年慢性病早期信号",
    "中老年骨骼关节保养",
    "中老年心脑血管养护",
    "中老年认知衰退预防",
    "中老年体检指标解读",
]

VIDEO_HOST_MARKERS = (
    "bilibili.com",
    "b23.tv",
    "youtube.com",
    "youtu.be",
    "youku.com",
    "iqiyi.com",
    "v.qq.com",
    "douyin.com",
    "tiktok.com",
    "vimeo.com",
    "dailymotion.com",
)


def site_key(url: str) -> str:
    if not url:
        return ""
    raw = url.strip()
    if "://" not in raw:
        raw = "https://" + raw
    try:
        host = (urlparse(raw).hostname or "").lower()
    except Exception:
        return ""
    if host.startswith("www."):
        host = host[4:]
    if host.endswith(".bilibili.com") or host == "b23.tv":
        return "bilibili.com"
    if host.endswith(".youtube.com") or host == "youtu.be":
        return "youtube.com"
    return host


def media_type(url: str, title: str, category: str, engines: list[str]) -> str:
    host = site_key(url)
    if any(host == m or host.endswith("." + m) for m in VIDEO_HOST_MARKERS):
        return "video"
    if category == "videos":
        return "video"
    joined = " ".join(engines).lower()
    if "bilibili" in joined or "youtube" in joined or "video" in joined:
        return "video"
    text = f"{url} {title}".lower()
    if re.search(r"/video/|/watch\?|bilibili\.com|youtu\.?be", text):
        return "video"
    return "article"


def fetch_one(base: str, query: str, categories: str, timeout: float) -> dict:
    params = urllib.parse.urlencode(
        {
            "q": query,
            "format": "json",
            "categories": categories,
        }
    )
    url = f"{base.rstrip('/')}/search?{params}"
    req = urllib.request.Request(
        url,
        headers={"User-Agent": "Chengwenfang-SearXNG-Report/1.0"},
    )
    with urllib.request.urlopen(req, timeout=timeout) as resp:
        body = resp.read().decode("utf-8", errors="replace")
    return json.loads(body)


def collect(base: str, queries: list[str], categories: str, timeout: float, pause: float) -> list[dict]:
    rows: list[dict] = []
    for index, query in enumerate(queries, start=1):
        print(f"[{index}/{len(queries)}] 搜索：{query}", flush=True)
        try:
            payload = fetch_one(base, query, categories, timeout)
        except Exception as exc:  # noqa: BLE001
            print(f"  ! 失败：{exc}", flush=True)
            rows.append(
                {
                    "search_query": query,
                    "title": "",
                    "url": "",
                    "site_key": "",
                    "media_type": "",
                    "category": "",
                    "engines": "",
                    "error": str(exc),
                }
            )
            continue
        results = payload.get("results") or []
        unresponsive = payload.get("unresponsive_engines") or []
        if unresponsive:
            print(f"  · 无响应引擎：{unresponsive}", flush=True)
        print(f"  · 返回 {len(results)} 条", flush=True)
        for item in results:
            url = (item.get("url") or "").strip()
            title = (item.get("title") or "").strip()
            engines = item.get("engines") or []
            if not engines and item.get("engine"):
                engines = [item.get("engine")]
            engines = [str(engine) for engine in engines if engine]
            category = (item.get("category") or "").strip()
            rows.append(
                {
                    "search_query": query,
                    "title": title,
                    "url": url,
                    "site_key": site_key(url),
                    "media_type": media_type(url, title, category, engines),
                    "category": category,
                    "engines": ",".join(engines),
                    "error": "",
                }
            )
        if pause > 0 and index < len(queries):
            time.sleep(pause)
    return rows


def write_csv(path: Path, rows: list[dict]) -> None:
    fields = [
        "search_query",
        "site_key",
        "media_type",
        "category",
        "engines",
        "title",
        "url",
        "error",
    ]
    with path.open("w", encoding="utf-8-sig", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=fields)
        writer.writeheader()
        for row in rows:
            writer.writerow({field: row.get(field, "") for field in fields})


def write_html(path: Path, rows: list[dict], meta: dict) -> None:
    ok_rows = [row for row in rows if row.get("url")]
    site_counts = Counter(row["site_key"] or "(空)" for row in ok_rows)
    media_counts = Counter(row["media_type"] or "(空)" for row in ok_rows)
    query_counts = Counter(row["search_query"] for row in ok_rows)
    by_site: dict[str, list[dict]] = defaultdict(list)
    for row in ok_rows:
        by_site[row["site_key"] or "(空)"].append(row)

    def esc(value: object) -> str:
        return html.escape("" if value is None else str(value))

    site_table = "".join(
        f"<tr><td>{esc(site)}</td><td>{count}</td><td>{count * 100 / max(len(ok_rows), 1):.1f}%</td></tr>"
        for site, count in site_counts.most_common()
    )
    media_table = "".join(
        f"<tr><td>{esc(kind)}</td><td>{count}</td></tr>"
        for kind, count in media_counts.most_common()
    )
    query_table = "".join(
        f"<tr><td>{esc(query)}</td><td>{count}</td></tr>"
        for query, count in query_counts.most_common()
    )

    sections = []
    for site, items in sorted(by_site.items(), key=lambda pair: (-len(pair[1]), pair[0])):
        lis = "".join(
            "<li>"
            f"<div><b>{esc(item['media_type'])}</b> · 词：{esc(item['search_query'])}"
            f" · 引擎：{esc(item['engines'] or '-')}</div>"
            f"<div>{esc(item['title'] or '(无标题)')}</div>"
            f"<div><a href=\"{esc(item['url'])}\" target=\"_blank\" rel=\"noreferrer\">{esc(item['url'])}</a></div>"
            "</li>"
            for item in items
        )
        sections.append(
            f"<section><h3>{esc(site)}（{len(items)}）</h3><ol>{lis}</ol></section>"
        )

    detail_rows = "".join(
        "<tr>"
        f"<td>{esc(row['search_query'])}</td>"
        f"<td>{esc(row['site_key'])}</td>"
        f"<td>{esc(row['media_type'])}</td>"
        f"<td>{esc(row['engines'])}</td>"
        f"<td>{esc(row['title'])}</td>"
        f"<td><a href=\"{esc(row['url'])}\" target=\"_blank\" rel=\"noreferrer\">{esc(row['url'])}</a></td>"
        "</tr>"
        for row in ok_rows
    )

    doc = f"""<!DOCTYPE html>
<html lang="zh-CN">
<head>
  <meta charset="utf-8" />
  <title>SearXNG 采集抽样报告</title>
  <style>
    body {{ font-family: "Segoe UI", "PingFang SC", "Microsoft YaHei", sans-serif; margin: 24px; color: #1f2937; }}
    h1, h2, h3 {{ margin: 0.6em 0 0.4em; }}
    .meta {{ color: #4b5563; margin-bottom: 1.5rem; }}
    table {{ border-collapse: collapse; width: 100%; margin: 0.8rem 0 1.4rem; }}
    th, td {{ border: 1px solid #d1d5db; padding: 6px 8px; text-align: left; vertical-align: top; }}
    th {{ background: #f3f4f6; }}
    ol {{ padding-left: 1.2rem; }}
    li {{ margin: 0.55rem 0; }}
    a {{ color: #1d4ed8; word-break: break-all; }}
    section {{ margin-bottom: 1.5rem; }}
  </style>
</head>
<body>
  <h1>SearXNG 采集抽样报告</h1>
  <div class="meta">
    <div>生成时间：{esc(meta['generated_at'])}</div>
    <div>实例：{esc(meta['base'])}</div>
    <div>categories：{esc(meta['categories'])}</div>
    <div>搜索词数：{esc(meta['query_count'])}</div>
    <div>有效结果总条数：{esc(len(ok_rows))}</div>
    <div>含错误行：{esc(meta['error_rows'])}</div>
  </div>

  <h2>按网站汇总</h2>
  <table>
    <thead><tr><th>网站 site_key</th><th>条数</th><th>占比</th></tr></thead>
    <tbody>{site_table}</tbody>
  </table>

  <h2>按媒体类型</h2>
  <table>
    <thead><tr><th>类型</th><th>条数</th></tr></thead>
    <tbody>{media_table}</tbody>
  </table>

  <h2>按搜索词</h2>
  <table>
    <thead><tr><th>搜索词</th><th>条数</th></tr></thead>
    <tbody>{query_table}</tbody>
  </table>

  <h2>分网站明细</h2>
  {''.join(sections)}

  <h2>全量明细表</h2>
  <table>
    <thead>
      <tr>
        <th>搜索词</th><th>网站</th><th>类型</th><th>引擎</th><th>标题</th><th>链接</th>
      </tr>
    </thead>
    <tbody>{detail_rows}</tbody>
  </table>
</body>
</html>
"""
    path.write_text(doc, encoding="utf-8")


def main() -> int:
    parser = argparse.ArgumentParser(description="SearXNG 一次性采集报告")
    parser.add_argument("--base", default="http://127.0.0.1:8080", help="SearXNG 根地址")
    parser.add_argument(
        "--categories",
        default="general,videos",
        help="与成文坊一致的 categories",
    )
    parser.add_argument("--timeout", type=float, default=25.0, help="单次请求超时秒")
    parser.add_argument("--pause", type=float, default=0.4, help="词与词之间间隔秒")
    parser.add_argument(
        "--out",
        default=".chengwenfang/reports",
        help="输出目录（相对仓库根或绝对路径）",
    )
    parser.add_argument(
        "--queries-file",
        default="",
        help="可选：每行一个搜索词的文本文件",
    )
    args = parser.parse_args()

    if args.queries_file:
        queries = [
            line.strip()
            for line in Path(args.queries_file).read_text(encoding="utf-8").splitlines()
            if line.strip() and not line.strip().startswith("#")
        ]
    else:
        queries = list(DEFAULT_QUERIES)

    out_dir = Path(args.out)
    if not out_dir.is_absolute():
        out_dir = Path.cwd() / out_dir
    out_dir.mkdir(parents=True, exist_ok=True)
    stamp = datetime.now().strftime("%Y%m%d-%H%M%S")
    html_path = out_dir / f"searxng-collect-{stamp}.html"
    csv_path = out_dir / f"searxng-collect-{stamp}.csv"

    print(f"SearXNG={args.base} queries={len(queries)} categories={args.categories}", flush=True)
    rows = collect(args.base, queries, args.categories, args.timeout, args.pause)
    write_csv(csv_path, rows)
    write_html(
        html_path,
        rows,
        {
            "generated_at": datetime.now().isoformat(timespec="seconds"),
            "base": args.base,
            "categories": args.categories,
            "query_count": len(queries),
            "error_rows": sum(1 for row in rows if row.get("error")),
        },
    )

    ok = [row for row in rows if row.get("url")]
    print("\n===== 摘要 =====", flush=True)
    print(f"总条数（有效 URL）：{len(ok)}", flush=True)
    for site, count in Counter(row["site_key"] or "(空)" for row in ok).most_common():
        print(f"  {site}: {count}", flush=True)
    print(f"\nHTML: {html_path}", flush=True)
    print(f"CSV : {csv_path}", flush=True)
    return 0


if __name__ == "__main__":
    sys.exit(main())
