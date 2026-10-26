# -*- coding: utf-8 -*-
from __future__ import annotations

import json
from pathlib import Path

ROOT = Path(__file__).resolve().parent
EXT = ROOT / "external-fixtures"
CLI = ROOT / "cli-fixtures"
EXT.mkdir(parents=True, exist_ok=True)
CLI.mkdir(parents=True, exist_ok=True)

(EXT / "rss-feed.xml").write_text(
    """<?xml version="1.0" encoding="UTF-8"?>
<rss version="2.0"><channel>
  <title>Golden Feed</title>
  <link>https://example.com</link>
  <item>
    <title>Golden RSS Headline About Model Release</title>
    <link>https://example.com/golden-rss-a</link>
    <pubDate>Tue, 21 Oct 2025 08:00:00 GMT</pubDate>
    <description>seed summary for golden radar</description>
  </item>
</channel></rss>
""",
    encoding="utf-8",
)

(EXT / "searxng-search.json").write_text(
    json.dumps(
        {
            "results": [
                {
                    "title": "Golden SearXNG Hit",
                    "url": "https://news.example.com/golden-searxng",
                    "content": "snippet from searxng",
                    "publishedDate": "2025-10-21T10:00:00Z",
                    "engines": ["bing"],
                }
            ]
        },
        ensure_ascii=False,
        indent=2,
    )
    + "\n",
    encoding="utf-8",
)

(EXT / "github-search.json").write_text(
    json.dumps(
        {
            "items": [
                {
                    "full_name": "owner/golden-repo",
                    "name": "golden-repo",
                    "html_url": "https://github.com/owner/golden-repo",
                    "description": "golden github hit",
                    "updated_at": "2025-10-21T08:00:00Z",
                    "created_at": "2025-01-01T00:00:00Z",
                }
            ]
        },
        ensure_ascii=False,
        indent=2,
    )
    + "\n",
    encoding="utf-8",
)

(EXT / "tavily-search.json").write_text(
    json.dumps(
        {
            "results": [
                {
                    "title": "Golden Tavily Hit",
                    "url": "https://news.example.com/golden-tavily",
                    "content": "snippet from tavily",
                    "published_date": "2025-10-21T09:00:00Z",
                }
            ]
        },
        ensure_ascii=False,
        indent=2,
    )
    + "\n",
    encoding="utf-8",
)

(EXT / "duckduckgo-html.html").write_text(
    """<html><body>
  <div class="result">
    <a class="result__a" href="{{ARTICLE_URL}}">Golden DDG Hit</a>
    <div class="result__snippet">Snippet about golden article</div>
  </div>
</body></html>
""",
    encoding="utf-8",
)

(EXT / "article-sample.html").write_text(
    """<html><head><title>Golden Article Title</title></head>
<body><article><p>Deep dive into golden research findings with enough text to exceed eighty characters of usable body content for the extractor pipeline and date probing.</p></article></body></html>
""",
    encoding="utf-8",
)

for name, body in {
    "wechat-stable-token.json": {"access_token": "GOLDEN_TOKEN", "expires_in": 7200},
    "wechat-upload-img.json": {"url": "https://mmbiz.qpic.cn/stub/golden.png"},
    "wechat-add-material.json": {"media_id": "MEDIA_COVER_GOLDEN"},
    "wechat-draft-add.json": {"media_id": "DRAFT_MEDIA_GOLDEN"},
    "wechat-draft-update.json": {"errcode": 0, "errmsg": "ok"},
}.items():
    (EXT / name).write_text(json.dumps(body, ensure_ascii=False) + "\n", encoding="utf-8")

BODY = (
    "Article body extracted for golden tests with more than eighty characters "
    "of real content for usability checks in the pipeline."
)
(CLI / "trafilatura-extract.json").write_text(
    json.dumps(
        {
            "ok": True,
            "text": BODY,
            "date": "2025-10-21",
            "dateInputText": "Published 2025-10-21",
            "error": None,
        },
        ensure_ascii=False,
        indent=2,
    )
    + "\n",
    encoding="utf-8",
)
(CLI / "lightpanda-extract.json").write_text(
    json.dumps(
        {
            "ok": True,
            "text": "Lightpanda " + BODY,
            "date": "2025-10-21",
            "dateInputText": "Published 2025-10-21",
            "error": None,
        },
        ensure_ascii=False,
        indent=2,
    )
    + "\n",
    encoding="utf-8",
)

cli_steps = {
    "probe_duration": {
        "ok": True,
        "step": "probe_duration",
        "text": "",
        "source": None,
        "error": None,
        "artifacts": {},
        "durationSeconds": 120.0,
    },
    "fetch_subtitle": {
        "ok": True,
        "step": "fetch_subtitle",
        "text": "黄金字幕正文：讲者介绍产品发布要点与时间线。",
        "source": "subtitle",
        "error": None,
        "artifacts": {"subtitle_path": "subs.vtt"},
        "durationSeconds": None,
    },
    "download_audio": {
        "ok": True,
        "step": "download_audio",
        "text": "",
        "source": None,
        "error": None,
        "artifacts": {"audio_path": "audio.m4a"},
        "durationSeconds": None,
    },
    "convert_format": {
        "ok": True,
        "step": "convert_format",
        "text": "",
        "source": None,
        "error": None,
        "artifacts": {"wav_path": "audio.wav"},
        "durationSeconds": None,
    },
    "asr": {
        "ok": True,
        "step": "asr",
        "text": "黄金 ASR 文本：补充口播细节。",
        "source": "asr",
        "error": None,
        "artifacts": {},
        "durationSeconds": None,
    },
    "download_video": {
        "ok": True,
        "step": "download_video",
        "text": "",
        "source": None,
        "error": None,
        "artifacts": {"video_path": "video.mp4"},
        "durationSeconds": None,
    },
    "extract_frames": {
        "ok": True,
        "step": "extract_frames",
        "text": "",
        "source": None,
        "error": None,
        "artifacts": {},
        "durationSeconds": None,
    },
}
for step, payload in cli_steps.items():
    (CLI / f"video-{step}.json").write_text(
        json.dumps(payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8"
    )

print(f"external={len(list(EXT.iterdir()))} cli={len(list(CLI.iterdir()))}")
