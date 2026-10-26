# -*- coding: utf-8 -*-
"""一次性生成 ai-fixtures；修改后重新运行本脚本。"""
from __future__ import annotations

import json
from pathlib import Path

ROOT = Path(__file__).resolve().parent
TINY_PNG = (
    "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg=="
)
LONG = "围绕本课主题展开讲解，给出可执行步骤与边界说明，帮助读者完成最小实践。" * 8

fixtures: list[tuple[str, dict]] = []


def add(name: str, **kwargs) -> None:
    kwargs.setdefault("scene", name)
    fixtures.append((f"{name}.json", kwargs))


add("connection_test", priority=10, matchAny=["Reply with OK"], content="OK", parser="text")

add(
    "theme_curriculum_generation",
    priority=20,
    matchAny=["routeNarrative", "theme_curriculum"],
    parser="curriculum",
    content={
        "schemaVersion": 1,
        "routeNarrative": "从入门到实践的学习路线",
        "series": [
            {
                "tempId": "s1",
                "name": "基础系列",
                "sortOrder": 1,
                "positioning": "打底",
                "lessons": [
                    {
                        "lessonNo": 1,
                        "title": "第一课",
                        "summary": "概览",
                        "learningOutcomes": ["能复述概念"],
                    }
                ],
            }
        ],
    },
)

# 课表 fixture 固定第一课标题为「第一课」；draft_core 的实际 heading 是「{title}的核心原理」。
CORE_HEADING = "第一课的核心原理"
EXAMPLE_HEADING = "它如何工作：具体例子与推导"

FULL_DRAFT = (
    f"# 黄金课程\n\n## 开场与本课目标\n从具体问题切入。{LONG}\n\n"
    f"## {CORE_HEADING}\n解释关键概念与边界。{LONG}\n\n"
    f"## {EXAMPLE_HEADING}\n通过例子巩固。{LONG}\n\n"
    "## 插图位置：结构关系\n\n```text\nconcept structure diagram\n```\n\n"
    f"## 本课小结\n- 理解本节主题\n- 完成一次最小实践\n{LONG}\n\n"
    f"## 下节课预告\n- 下一课将继续扩展应用场景\n{LONG}\n"
)

for scene, needles, title in [
    (
        "draft_opening",
        # 必须带「本节标题：」前缀；裸标题会出现在 previousTail 里导致后段串匹配。
        ["本节标题：开场与本课目标"],
        f"## 开场与本课目标\n从具体问题切入。{LONG}",
    ),
    (
        "draft_core",
        # heading 为「{title}的核心原理」，不能写「本节标题：核心原理」。
        # 用本节任务独有句（不会进入 previousTail）。
        ["完整解释本课必须覆盖的概念、术语、边界和必要公式"],
        f"## {CORE_HEADING}\n解释关键概念与边界。{LONG}",
    ),
    (
        "draft_example",
        ["用零基础读者可跟随的例子、计算或推导巩固核心原理"],
        (
            f"## {EXAMPLE_HEADING}\n通过例子巩固。{LONG}\n\n"
            "## 插图位置：结构关系\n\n```text\nconcept structure diagram\n```\n\n"
            "## 插图位置：操作示意\n\n```text\noperation screenshot diagram\n```"
        ),
    ),
    (
        "draft_summary",
        ["本节标题：本课小结"],
        f"## 本课小结\n- 理解本节主题\n- 完成一次最小实践\n{LONG}",
    ),
    (
        "draft_preview",
        ["本节标题：下节课预告"],
        f"## 下节课预告\n- 下一课将继续扩展应用场景\n{LONG}",
    ),
    (
        "draft_compress",
        ["请编辑下面的完整课程原稿", "需要压缩的原稿"],
        FULL_DRAFT,
    ),
]:
    add(scene, priority=30, matchAny=needles, content=title, parser="text")

add(
    "wechat_chunk",
    priority=40,
    matchAny=["转换为公众号 Markdown", "请将下面课程原稿的第"],
    content=(
        f"转换后的公众号段落。{LONG}\n\n[[COURSE_IMAGE_1]]\n\n"
        "## 本课小结\n- 理解本节主题\n\n## 下节课预告\n- 下一课将继续扩展应用场景"
    ),
    parser="text",
)
add(
    "course_draft",
    priority=25,
    matchAny=["请生成课程原稿"],
    content=FULL_DRAFT,
    parser="text",
)
add(
    "wechat_article_stub",
    priority=25,
    matchAny=["请生成公众号文章"],
    content=(
        f"# 公众号文章\n\n转换后的公众号正文。{LONG}\n\n"
        "## 本课小结\n- 理解本节主题\n\n## 下节课预告\n- 下一课将继续扩展"
    ),
    parser="text",
)
add(
    "article_repair",
    priority=50,
    matchAny=["Fix forbidden", "article_repair", "请修复"],
    content=f"## 修复后正文\n{LONG}",
    parser="text",
)

add(
    "hot_news_theme_search_queries",
    priority=20,
    # 勿加「资讯采集编辑」：来源渠道 Skill 正文也含该串，会抢先命中本规则。
    matchAny=["短搜索词"],
    parser="queries",
    content={
        "queries": [
            "睡眠机制",
            "褪黑素误区",
            "深度睡眠指南",
            "咖啡因影响睡眠",
            "午睡时长研究",
            "失眠风险信号",
        ]
    },
)
add(
    "hot_news_theme_github_query",
    priority=20,
    matchAny=["GitHub Search", "开源情报采集编辑"],
    parser="github_query",
    content={"query": "AI OR LLM OR agent"},
)
add(
    "hot_news_theme_scoring_rubric",
    priority=20,
    matchAny=["写作价值评分量表", "内容选题主编"],
    parser="scoring_rubric",
    content={
        "dimensions": [
            {"key": "theme_fit", "label": "主题契合", "description": "是否贴合主题读者", "weight": 34},
            {"key": "depth", "label": "可挖深度", "description": "是否值得深写", "weight": 33},
            {"key": "source_trust", "label": "来源可信", "description": "一手与权威", "weight": 33},
        ],
        "scoreGuidance": "依据来源域名判断可信度，避免纯热度维度",
    },
)
add(
    "hot_news_theme_source_channels",
    priority=20,
    # 用用户提示独有句；勿仅靠「资讯采集编辑」（与搜索词 Skill 撞车）。
    matchAny=["已占用网页 URL（禁止再次推荐）", "已占用网页 URL"],
    parser="channels",
    content={
        "channels": [
            {
                "name": "示例RSS",
                "type": "rss",
                "baseUrl": "https://example.com/feed.xml",
                "config": {"urls": [], "queries": [], "maxResults": 25, "query": ""},
            }
        ]
    },
)

add(
    "radar_article_fact_brief",
    priority=30,
    matchAny=[
        "根据给定标题与正文，输出一段「事实速览」",
        "不要输出标题、列表符号、JSON 或「速览：」前缀",
    ],
    content=(
        "Sleep research shows deep sleep consolidates memory; the brief also covers "
        "common melatonin misconceptions with checkable findings for clustering and scoring."
    ),
    parser="text",
)
add(
    "radar_article_publish_date",
    priority=30,
    matchAny=["publishedAt", "推断发布日期"],
    parser="publish_date",
    content={"publishedAt": "2024-08-27", "reason": "文头日期"},
)
add(
    "radar_video_fact_brief",
    priority=30,
    matchAny=["口播/字幕", "视频事实速览"],
    content="视频口播事实速览：讲者介绍产品发布要点与时间线，未出现明显推销话术。",
    parser="text",
)
add(
    "radar_candidate_filter_marketing",
    priority=30,
    matchAny=["事实速览：", "原文摘录"],
    parser="marketing",
    content={"marketing": False, "reason": "知识讲解无引流"},
)
add(
    "radar_cluster_summary_merge",
    priority=30,
    matchAny=["成员速览", "选题标题："],
    content=(
        "Merged topic brief: sleep research and melatonin coverage align on memory "
        "restoration and common supplement myths with checkable claims."
    ),
    parser="text",
)
add(
    "radar_ai_theme_seed",
    priority=30,
    matchAny=["今日可写的主题种子", "主题问卷"],
    parser="theme_seed",
    content={
        "items": [
            {
                "title": "开源模型评测口径为何又变了",
                "summary": (
                    "近况是主流榜单调整维度，创作者需要重新解释分数含义。"
                    "值得写是因为读者正被新旧口径混淆。建议从一次实测对比切入。"
                    + ("补充说明。" * 8)
                ),
                "signals": ["榜单变更", "实测对比"],
                "angle": "评测口径变化",
            }
        ]
    },
)
add(
    "hot_news_topic_writing_score",
    priority=30,
    matchAny=["editorReason", "recommendUse"],
    parser="writing_score",
    content={
        "dimensions": [
            {"key": "theme_fit", "score": 8, "reason": "贴合"},
            {"key": "depth", "score": 7, "reason": "可挖"},
            {"key": "source_trust", "score": 7, "reason": "来源尚可"},
        ],
        "editorReason": "值得跟进",
        "recommendUse": "lead",
        "riskNote": "",
    },
)
add(
    "hot_news_topic_translation",
    priority=30,
    matchAny=["hot_news_topic_translation", "translations"],
    parser="topic_translation",
    content={
        "translations": [
            {"id": "t1", "title": "英伟达押注新架构", "summary": "中文速览说明事件要点"}
        ]
    },
)
add(
    "hot_news_topic_research_keywords",
    priority=20,
    matchAny=["热点选题资料检索助手", "disambiguation"],
    parser="keyword_plan",
    content={
        "subject": "Apple Inc",
        "keywords": ["Apple Inc", "Apple 公司"],
        "disambiguation": "苹果公司，不是水果",
    },
)
add(
    "hot_news_topic_research_relevance",
    priority=20,
    matchAny=["事实核验编辑", '"relevant"'],
    parser="relevance",
    content={"items": [{"id": "0", "relevant": True, "reason": "同一公司财报"}]},
)
add(
    "hot_news_topic_research_translate",
    priority=20,
    matchAny=["中文资讯编辑", "中文标题"],
    parser="research_translate",
    content={"items": [{"id": "0", "title": "英伟达押注 Reflection", "summary": "摘要"}]},
)
add(
    "hot_news_research_item_score",
    priority=20,
    matchAny=["固定五维量表", "writability"],
    parser="research_item_score",
    content={
        "items": [
            {
                "id": "0",
                "dimensions": [
                    {"key": "relevance", "score": 8, "reason": "直接相关"},
                    {"key": "credibility", "score": 7, "reason": "权威媒体"},
                    {"key": "freshness", "score": 6, "reason": "近一周"},
                    {"key": "independence", "score": 5, "reason": "补充视角"},
                    {"key": "writability", "score": 5, "reason": "可作背景"},
                ],
                "recommendUse": "priority",
                "riskNote": "",
            }
        ]
    },
)
add(
    "hot_news_video_parse_summarize",
    priority=30,
    matchAny=["视频标题：", "原文："],
    content="视频语音摘要：讲者梳理发布会要点与时间线，结论偏向产品能力边界说明。",
    parser="text",
)
add(
    "hot_news_video_parse_vision_summarize",
    priority=30,
    matchAny=["关键帧", "视觉摘要"],
    content="关键帧视觉摘要：画面出现产品界面与数据图表，未见明显广告贴片。",
    parser="text",
)
add(
    "hot_news_source_video_fact_brief",
    priority=25,
    matchAny=["供热点选题引用", "无可引用事实"],
    content="信源视频事实速览：深度睡眠有助于巩固记忆，并说明褪黑素补充的常见误区与可核对要点。",
    parser="text",
)
add(
    "hot_news_source_video_promo_judge",
    priority=25,
    matchAny=["内容风控编辑", "knowledge|marketing"],
    parser="promo_judge",
    content={"intent": "knowledge", "reason": "科普讲解睡眠机制，非带货"},
)
add(
    "hot_news_article_skeleton",
    priority=20,
    matchAny=["文章骨架", "workingTitle", "配图规范 Skill"],
    parser="skeleton",
    content={
        "workingTitle": "Beam 发布",
        "scope": "只谈开源模型",
        "coreThesis": "后训练进入新阶段",
        "coreEvent": "Beam 发布",
        "keyInsights": ["洞察一", "洞察二"],
        "impacts": ["影响一"],
        "readerSignals": ["信号一"],
        "narrativeShape": "冲突推进",
        "sectionPlan": ["开场", "收束"],
        "illustrationStyle": "对照示意图",
        "coverBrief": "Beam 发布冲突感封面",
        "imagePromptRequirements": "无文字水印",
        "recommendedWordCountOption": "2500-4000",
        "recommendedImageCountOption": "3-5",
        "architecture": ["事实", "影响"],
        "flow": ["开场", "收束"],
        "modules": [{"name": "事实", "purpose": "交代", "keyPoints": ["发布"]}],
        "referenceHints": ["CSDN 简报"],
    },
)

draft_md = (
    f"## 热点正文\n{LONG}\n\n**插图位置：对照**\n\n```text\ncontrast diagram\n```"
)
for scene, needle in [
    ("hot_news_prepare_facts", "已确认可用事实包"),
    ("hot_news_generate_draft", "请根据「文章骨架」生成完整热点原稿"),
    ("hot_news_review_draft", "审阅热点原稿"),
    ("hot_news_generate_wechat", "生成公众号"),
    ("hot_news_review_wechat", "审阅公众号"),
]:
    if scene == "hot_news_prepare_facts":
        add(
            scene,
            priority=40,
            matchAny=[needle],
            parser="json_object",
            content={"facts": ["事实1"], "timeline": ["T0"]},
        )
    else:
        add(scene, priority=40, matchAny=[needle], parser="text", content=draft_md)

add(
    "hot_news_rewrite",
    priority=40,
    matchAny=["按以下要求改写文章"],
    content=f"## 改写后正文\n{LONG}",
    parser="text",
)
add(
    "hot_news_review_repair",
    priority=40,
    matchAny=["请修复以下热点文章"],
    content=f"## 修复后热点正文\n{LONG}",
    parser="text",
)
add(
    "adhoc",
    priority=20,
    matchAny=["请输出 actions JSON", '"actions"'],
    parser="adhoc_actions",
    content={"actions": [{"op": "write_draft", "content": "# 自定义草稿\n正文"}]},
)
add(
    "openai_responses_sample",
    endpoint="responses",
    priority=15,
    matchAny=["RESPONSES_PROTOCOL_PROBE"],
    content="responses-ok",
    parser="text",
)
add(
    "image",
    endpoint="images",
    priority=10,
    matchAny=[],
    matchPath="/v1/images/generations",
    b64Json=TINY_PNG,
    parser="image",
)

catalog = []
for name, data in fixtures:
    if data.get("endpoint") == "images":
        data.pop("content", None)
    (ROOT / name).write_text(
        json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8"
    )
    catalog.append(name)

(ROOT / "catalog.json").write_text(
    json.dumps(catalog, ensure_ascii=False, indent=2) + "\n", encoding="utf-8"
)
print(f"wrote {len(fixtures)} fixtures -> {ROOT}")
