#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
SearXNG 采集漏斗报告（独立脚本，不进入业务流水线）。

对齐工作台获取阶段主要过滤：每词条数上限、硬广、时效、去重、拉黑、标题主题、视频配额。
不模拟：解析速览失败、素材筛选里的模型营销判定（需完整 Job）。

用法：
  python scripts/searxng-filter-funnel-report.py
  python scripts/searxng-filter-funnel-report.py --hours 24 --max-results 25 --max-article 50 --max-video 10
"""

from __future__ import annotations

import argparse
import csv
import html
import json
import re
import sys
import time
import urllib.parse
import urllib.request
from collections import Counter, defaultdict
from datetime import datetime, timedelta
from pathlib import Path
from urllib.parse import urlparse

DEFAULT_THEME_NAME = "中老年身心健康"
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

# 与库中生效拉黑一致（可被 --blocks-file 覆盖）
DEFAULT_BLOCKS = [
    ("中老年体检指标解读", "bilibili.com"),
    ("中老年认知衰退预防", "bilibili.com"),
    ("中老年运动安全注意事项", "bilibili.com"),
    ("中老年骨骼关节保养", "bilibili.com"),
]

VIDEO_HOSTS = (
    "bilibili.com",
    "b23.tv",
    "youtube.com",
    "youtu.be",
    "youku.com",
    "iqiyi.com",
    "v.qq.com",
    "douyin.com",
    "tiktok.com",
)

# 过滤阶段中文名
STAGE_PASS = "保留（通过获取阶段）"
STAGE_MAX = "每词条数上限截断"
STAGE_ADS = "硬广页面过滤"
STAGE_STALE = "发布时间超出回溯窗口"
STAGE_DEDUP = "跨词链接去重"
STAGE_BLOCK = "搜索词与网站拉黑"
STAGE_THEME = "标题主题不相关"
STAGE_ARTICLE_QUOTA = "超出文章素材配额"
STAGE_VIDEO_QUOTA = "超出视频素材配额"

TOKEN_PATTERN = re.compile(r"[\u4e00-\u9fffA-Za-z0-9]{2,}")
URL_CALENDAR = re.compile(r"/(20\d{2})[-/](\d{2})[-/](\d{2})(?:/|$)")


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


def media_type_zh(url: str, title: str, category: str, engines: list[str]) -> str:
    host = site_key(url)
    if any(host == marker or host.endswith("." + marker) for marker in VIDEO_HOSTS):
        return "视频"
    if category == "videos":
        return "视频"
    joined = " ".join(engines).lower()
    if "bilibili" in joined or "youtube" in joined or "video" in joined:
        return "视频"
    text = f"{url} {title}".lower()
    if re.search(r"/video/|/watch\?|bilibili\.com|youtu\.?be", text):
        return "视频"
    return "文章"


def resolve_time_range(hours: int | None) -> str:
    if hours is None or hours <= 0:
        return "month"
    if hours <= 36:
        return "day"
    if hours <= 240:
        return "week"
    return "month"


def parse_published(raw: str | None) -> datetime | None:
    if not raw or not str(raw).strip():
        return None
    text = str(raw).strip()
    try:
        if text.endswith("Z"):
            return datetime.fromisoformat(text.replace("Z", "+00:00")).replace(tzinfo=None)
        if "+" in text[10:] or text.count("-") >= 3 and "T" in text:
            return datetime.fromisoformat(text).replace(tzinfo=None)
    except Exception:
        pass
    try:
        normalized = text.replace(" ", "T")
        return datetime.fromisoformat(normalized[:19])
    except Exception:
        return None


def infer_from_url(url: str) -> datetime | None:
    match = URL_CALENDAR.search(url or "")
    if not match:
        return None
    try:
        return datetime(int(match.group(1)), int(match.group(2)), int(match.group(3)))
    except Exception:
        return None


def resolve_published(published_date: str | None, pubdate: str | None, url: str) -> datetime | None:
    return parse_published(published_date) or parse_published(pubdate) or infer_from_url(url)


def looks_like_marketing(url: str, title: str) -> bool:
    text = f"{url} {title}".lower()
    needles = (
        "/product",
        "/products/",
        "官方旗舰",
        "立即购买",
        "立即下单",
        "促销",
        "优惠券",
        "加购",
        "sku",
    )
    return any(needle in text for needle in needles)


def normalize_text(value: str) -> str:
    return re.sub(r"\s+", "", (value or "").lower())


def build_anchors(theme_name: str, queries: list[str]) -> list[str]:
    anchors: list[str] = []
    seen: set[str] = set()

    def add_phrase(phrase: str) -> None:
        normalized = normalize_text(phrase)
        if not normalized:
            return
        if len(normalized) >= 2 and normalized not in seen:
            seen.add(normalized)
            anchors.append(normalized)
        for token in TOKEN_PATTERN.findall(normalized):
            if len(token) >= 2 and token not in seen:
                seen.add(token)
                anchors.append(token)
            if len(token) >= 3:
                max_len = min(6, len(token))
                for length in range(3, max_len + 1):
                    for index in range(0, len(token) - length + 1):
                        piece = token[index : index + length]
                        if piece not in seen:
                            seen.add(piece)
                            anchors.append(piece)

    add_phrase(theme_name)
    for query in queries:
        add_phrase(query)
    return anchors


def theme_relevant(title: str, anchors: list[str]) -> tuple[bool, str]:
    text = normalize_text(title)
    if not text:
        return False, "标题为空，无法判断主题相关"
    if not anchors:
        return True, "无可用锚点，放行"
    strong_hits = 0
    weak_hits = 0
    strong_sample = ""
    for anchor in anchors:
        if anchor not in text:
            continue
        if len(anchor) >= 3:
            strong_hits += 1
            if not strong_sample:
                strong_sample = anchor
        elif len(anchor) >= 2:
            weak_hits += 1
    if strong_hits > 0:
        return True, f"命中主题强锚点：{strong_sample}"
    if weak_hits >= 2:
        return True, f"命中主题弱锚点 {weak_hits} 个"
    return False, "标题与主题信号无明显重合"


def fetch_one(base: str, query: str, categories: str, time_range: str, timeout: float) -> dict:
    params = urllib.parse.urlencode(
        {
            "q": query,
            "format": "json",
            "categories": categories,
            "time_range": time_range,
        }
    )
    url = f"{base.rstrip('/')}/search?{params}"
    req = urllib.request.Request(url, headers={"User-Agent": "Workbench-SearXNG-Funnel/1.0"})
    with urllib.request.urlopen(req, timeout=timeout) as resp:
        return json.loads(resp.read().decode("utf-8", errors="replace"))


def collect_raw(
    base: str,
    queries: list[str],
    categories: str,
    time_range: str,
    timeout: float,
    pause: float,
) -> list[dict]:
    rows: list[dict] = []
    for index, query in enumerate(queries, start=1):
        print(f"[{index}/{len(queries)}] 搜索：{query}", flush=True)
        try:
            payload = fetch_one(base, query, categories, time_range, timeout)
            results = payload.get("results") or []
            unresponsive = payload.get("unresponsive_engines") or []
            if unresponsive:
                print(f"  · 无响应引擎数：{len(unresponsive)}", flush=True)
            print(f"  · 返回 {len(results)} 条", flush=True)
            for position, item in enumerate(results, start=1):
                url = (item.get("url") or "").strip()
                title = (item.get("title") or "").strip()
                engines = item.get("engines") or ([] if not item.get("engine") else [item.get("engine")])
                engines = [str(engine) for engine in engines if engine]
                category = (item.get("category") or "").strip()
                published = resolve_published(item.get("publishedDate"), item.get("pubdate"), url)
                rows.append(
                    {
                        "搜索词": query,
                        "标题": title,
                        "链接": url,
                        "网站": site_key(url),
                        "类型": media_type_zh(url, title, category, engines),
                        "上游引擎": "、".join(engines) if engines else "",
                        "上游分类": "视频" if category == "videos" else ("综合" if category == "general" else category),
                        "发布时间": published.strftime("%Y-%m-%d %H:%M:%S") if published else "",
                        "发布时间对象": published,
                        "上游序号": position,
                        "过滤阶段": "",
                        "过滤说明": "",
                    }
                )
        except Exception as exc:  # noqa: BLE001
            print(f"  ! 失败：{exc}", flush=True)
        if pause > 0 and index < len(queries):
            time.sleep(pause)
    return rows


def apply_funnel(
    rows: list[dict],
    *,
    max_results: int,
    hours: int,
    blocks: set[tuple[str, str]],
    theme_name: str,
    queries: list[str],
    max_article: int,
    max_video: int,
) -> list[dict]:
    anchors = build_anchors(theme_name, queries)
    now = datetime.now()
    cutoff = now - timedelta(hours=hours) if hours and hours > 0 else None

    # 1) 每词条数上限（按上游顺序）
    per_query_kept: dict[str, int] = defaultdict(int)
    after_max: list[dict] = []
    for row in rows:
        query = row["搜索词"]
        if per_query_kept[query] >= max_results:
            row["过滤阶段"] = STAGE_MAX
            row["过滤说明"] = f"该搜索词已保留 {max_results} 条，后续结果截断"
            continue
        per_query_kept[query] += 1
        after_max.append(row)

    # 2) 硬广 / 过旧时效 / 拉黑 / 主题 / 去重（无发布时间不再丢弃）
    seen_urls: set[str] = set()
    survivors: list[dict] = []
    for row in after_max:
        url = row["链接"]
        title = row["标题"]
        if looks_like_marketing(url, title):
            row["过滤阶段"] = STAGE_ADS
            row["过滤说明"] = "标题或链接疑似硬广/推销落地页"
            continue
        published = row.get("发布时间对象")
        if cutoff is not None and published is not None and published < cutoff:
            row["过滤阶段"] = STAGE_STALE
            row["过滤说明"] = f"发布时间早于回溯窗口（{hours} 小时）"
            continue
        key = (row["搜索词"], row["网站"])
        if key in blocks:
            row["过滤阶段"] = STAGE_BLOCK
            row["过滤说明"] = f"主题已拉黑组合：{row['搜索词']} × {row['网站']}"
            continue
        ok, reason = theme_relevant(title, anchors)
        if not ok:
            row["过滤阶段"] = STAGE_THEME
            row["过滤说明"] = reason
            continue
        url_key = url.lower()
        if not url_key or url_key in seen_urls:
            row["过滤阶段"] = STAGE_DEDUP
            row["过滤说明"] = "与更早搜索词命中的同一链接重复"
            continue
        seen_urls.add(url_key)
        survivors.append(row)

    # 3) 有发布时间优先，再按文章/视频配额裁剪
    def publish_priority(row: dict) -> tuple[int, float]:
        published = row.get("发布时间对象")
        if published is None:
            return (1, 0.0)
        return (0, -published.timestamp())

    survivors.sort(key=publish_priority)
    article_kept = 0
    video_kept = 0
    for row in survivors:
        is_video = row["类型"] == "视频"
        if is_video:
            if video_kept >= max_video:
                row["过滤阶段"] = STAGE_VIDEO_QUOTA
                row["过滤说明"] = f"本轮视频素材配额已满（上限 {max_video}）；无时间素材会排在有时间之后"
            else:
                video_kept += 1
                row["过滤阶段"] = STAGE_PASS
                row["过滤说明"] = (
                    "通过获取阶段过滤"
                    if row.get("发布时间对象")
                    else "无发布时间，保留但优先级靠后"
                )
        elif article_kept >= max_article:
            row["过滤阶段"] = STAGE_ARTICLE_QUOTA
            row["过滤说明"] = f"本轮文章素材配额已满（上限 {max_article}）；无时间素材会排在有时间之后"
        else:
            article_kept += 1
            row["过滤阶段"] = STAGE_PASS
            row["过滤说明"] = (
                "通过获取阶段过滤"
                if row.get("发布时间对象")
                else "无发布时间，保留但优先级靠后"
            )

    return rows


def write_csv(path: Path, rows: list[dict]) -> None:
    fields = [
        "搜索词",
        "网站",
        "类型",
        "发布时间",
        "过滤阶段",
        "过滤说明",
        "上游引擎",
        "上游分类",
        "上游序号",
        "标题",
        "链接",
    ]
    with path.open("w", encoding="utf-8-sig", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=fields, extrasaction="ignore")
        writer.writeheader()
        for row in rows:
            writer.writerow({field: row.get(field, "") for field in fields})


def write_html(path: Path, rows: list[dict], meta: dict) -> None:
    def esc(value: object) -> str:
        return html.escape("" if value is None else str(value))

    stage_counts = Counter(row.get("过滤阶段") or "（未标注）" for row in rows)
    pass_rows = [row for row in rows if row.get("过滤阶段") == STAGE_PASS]
    site_pass = Counter(row["网站"] or "（空）" for row in pass_rows)

    stage_table = "".join(
        f"<tr><td>{esc(stage)}</td><td>{count}</td></tr>"
        for stage, count in stage_counts.most_common()
    )
    site_table = "".join(
        f"<tr><td>{esc(site)}</td><td>{count}</td></tr>"
        for site, count in site_pass.most_common()
    )
    detail = "".join(
        "<tr>"
        f"<td>{esc(row.get('搜索词'))}</td>"
        f"<td>{esc(row.get('网站'))}</td>"
        f"<td>{esc(row.get('类型'))}</td>"
        f"<td>{esc(row.get('发布时间') or '无')}</td>"
        f"<td>{esc(row.get('过滤阶段'))}</td>"
        f"<td>{esc(row.get('过滤说明'))}</td>"
        f"<td>{esc(row.get('标题'))}</td>"
        f"<td><a href=\"{esc(row.get('链接'))}\" target=\"_blank\" rel=\"noreferrer\">{esc(row.get('链接'))}</a></td>"
        "</tr>"
        for row in rows
        if row.get("链接")
    )

    doc = f"""<!DOCTYPE html>
<html lang="zh-CN">
<head>
  <meta charset="utf-8" />
  <title>SearXNG 采集过滤漏斗报告</title>
  <style>
    body {{ font-family: "Segoe UI", "PingFang SC", "Microsoft YaHei", sans-serif; margin: 24px; color: #111827; }}
    h1,h2 {{ margin: 0.8em 0 0.4em; }}
    .meta {{ color: #4b5563; line-height: 1.6; margin-bottom: 1.2rem; }}
    table {{ border-collapse: collapse; width: 100%; margin: 0.8rem 0 1.4rem; font-size: 14px; }}
    th, td {{ border: 1px solid #d1d5db; padding: 6px 8px; text-align: left; vertical-align: top; }}
    th {{ background: #f3f4f6; }}
    a {{ color: #1d4ed8; word-break: break-all; }}
  </style>
</head>
<body>
  <h1>SearXNG 采集过滤漏斗报告</h1>
  <div class="meta">
    <div>生成时间：{esc(meta['generated_at'])}</div>
    <div>主题：{esc(meta['theme_name'])}</div>
    <div>实例：{esc(meta['base'])}</div>
    <div>分类：综合 + 视频</div>
    <div>时间范围参数：{esc(meta['time_range_label'])}（回溯 {esc(meta['hours'])} 小时）</div>
    <div>每词条数上限：{esc(meta['max_results'])}</div>
    <div>文章素材配额：{esc(meta['max_article'])}</div>
    <div>视频素材配额：{esc(meta['max_video'])}</div>
    <div>上游原始条数：{esc(meta['raw_count'])}</div>
    <div>通过获取阶段：{esc(len(pass_rows))}</div>
    <div>说明：本报告模拟「获取渠道资源」阶段；不含解析失败与模型营销筛选。</div>
  </div>

  <h2>各过滤阶段条数</h2>
  <table>
    <thead><tr><th>过滤阶段</th><th>条数</th></tr></thead>
    <tbody>{stage_table}</tbody>
  </table>

  <h2>通过获取阶段后的网站分布</h2>
  <table>
    <thead><tr><th>网站</th><th>条数</th></tr></thead>
    <tbody>{site_table or '<tr><td colspan="2">无</td></tr>'}</tbody>
  </table>

  <h2>全量明细</h2>
  <table>
    <thead>
      <tr>
        <th>搜索词</th><th>网站</th><th>类型</th><th>发布时间</th>
        <th>过滤阶段</th><th>过滤说明</th><th>标题</th><th>链接</th>
      </tr>
    </thead>
    <tbody>{detail}</tbody>
  </table>
</body>
</html>
"""
    path.write_text(doc, encoding="utf-8")


def load_blocks(path: str) -> set[tuple[str, str]]:
    if not path:
        return set(DEFAULT_BLOCKS)
    rows = Path(path).read_text(encoding="utf-8").splitlines()
    out: set[tuple[str, str]] = set()
    for line in rows:
        line = line.strip()
        if not line or line.startswith("#"):
            continue
        parts = [part.strip() for part in line.split("|")]
        if len(parts) >= 2:
            out.add((parts[0], parts[1]))
    return out


def main() -> int:
    parser = argparse.ArgumentParser(description="SearXNG 过滤漏斗报告")
    parser.add_argument("--base", default="http://127.0.0.1:8080")
    parser.add_argument("--categories", default="general,videos")
    parser.add_argument("--hours", type=int, default=24, help="采集回溯小时，对齐雷达 hours")
    parser.add_argument("--max-results", type=int, default=25)
    parser.add_argument("--max-article", type=int, default=50)
    parser.add_argument("--max-video", type=int, default=10)
    parser.add_argument("--timeout", type=float, default=25.0)
    parser.add_argument("--pause", type=float, default=0.35)
    parser.add_argument("--theme-name", default=DEFAULT_THEME_NAME)
    parser.add_argument("--out", default=".workbench/reports")
    parser.add_argument("--blocks-file", default="", help="每行：搜索词|网站")
    parser.add_argument("--queries-file", default="")
    args = parser.parse_args()

    queries = list(DEFAULT_QUERIES)
    if args.queries_file:
        queries = [
            line.strip()
            for line in Path(args.queries_file).read_text(encoding="utf-8").splitlines()
            if line.strip() and not line.strip().startswith("#")
        ]
    blocks = load_blocks(args.blocks_file)
    time_range = resolve_time_range(args.hours)
    time_range_label = {"day": "一天内", "week": "一周内", "month": "一月内"}.get(time_range, time_range)

    out_dir = Path(args.out)
    if not out_dir.is_absolute():
        out_dir = Path.cwd() / out_dir
    out_dir.mkdir(parents=True, exist_ok=True)
    stamp = datetime.now().strftime("%Y%m%d-%H%M%S")
    html_path = out_dir / f"searxng-漏斗报告-{stamp}.html"
    csv_path = out_dir / f"searxng-漏斗报告-{stamp}.csv"

    print(
        f"实例={args.base} 词数={len(queries)} 每词上限={args.max_results} "
        f"回溯小时={args.hours} 时间范围={time_range_label} "
        f"文章配额={args.max_article} 视频配额={args.max_video}",
        flush=True,
    )
    raw_rows = collect_raw(
        args.base, queries, args.categories, time_range, args.timeout, args.pause
    )
    rows = apply_funnel(
        raw_rows,
        max_results=args.max_results,
        hours=args.hours,
        blocks=blocks,
        theme_name=args.theme_name,
        queries=queries,
        max_article=args.max_article,
        max_video=args.max_video,
    )
    # 清理不可序列化字段
    for row in rows:
        row.pop("发布时间对象", None)

    write_csv(csv_path, rows)
    write_html(
        html_path,
        rows,
        {
            "generated_at": datetime.now().strftime("%Y-%m-%d %H:%M:%S"),
            "theme_name": args.theme_name,
            "base": args.base,
            "hours": args.hours,
            "time_range_label": time_range_label,
            "max_results": args.max_results,
            "max_article": args.max_article,
            "max_video": args.max_video,
            "raw_count": len([row for row in rows if row.get("链接")]),
        },
    )

    print("\n===== 各阶段条数 =====", flush=True)
    for stage, count in Counter(row.get("过滤阶段") for row in rows if row.get("链接")).most_common():
        print(f"  {stage}: {count}", flush=True)
    passed = sum(1 for row in rows if row.get("过滤阶段") == STAGE_PASS)
    print(f"\n通过获取阶段：{passed}", flush=True)
    print(f"网页报告：{html_path}", flush=True)
    print(f"表格文件：{csv_path}", flush=True)
    return 0


if __name__ == "__main__":
    sys.exit(main())
