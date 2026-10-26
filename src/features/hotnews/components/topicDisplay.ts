import { useEffect, useRef, useState } from "react";
import {
  Activity,
  Check,
  CheckCircle2,
  ChevronDown,
  ChevronLeft,
  ChevronRight,
  ExternalLink,
  FileText,
  Globe2,
  Maximize2,
  RefreshCcw,
  Rss,
  Search,
  ShieldCheck,
  X,
} from "lucide-react";
import { loadJob, type JobData } from "../../../shared/api/jobs";
import { formatPublishDate } from "../../../shared/format/time";
import { usePolling } from "../../../shared/hooks/usePolling";
import {
  LIST_PAGE_SIZE_OPTIONS,
  listPageNumbers,
  type ListPageSize,
} from "../../../shared/api/pagination";
import {
  listHotNewsRuns,
  listHotNewsSources,
  listHotNewsTopicSources,
  listHotNewsEvidence,
  listHotNewsBriefs,
  listHotNewsArticles,
  listHotNewsRadarCollectRuns,
  pageHotNewsTopics,
  ensureHotNewsArticle,
  startHotNewsFetch,
  updateHotNewsTopicStatus,
  withDocumentIdQuery,
} from "../api";
import type {
  HotNewsArticle,
  HotNewsBrief,
  HotNewsEvidence,
  HotNewsRadarTopicFilter,
  HotNewsRadarTopicSort,
  HotNewsRun,
  HotNewsSource,
  HotNewsTopic,
  HotNewsTopicSource,
} from "../api";
import {
  ChannelType,
  channelTypeShortLabel,
  isAiThemeSeedTopic,
  TOPIC_ORIGIN_LABELS,
  TopicOriginKind,
} from "../model";
import {
  assertWorkflowFeatureAvailable,
  WorkflowFeatureKey,
} from "../../workflow/api";
import { formatRadarCollectOuterStatus } from "../pages/HotNewsRadarCollectExecutionPage";

export const isConfirmedTopic = (status: string) =>
  status === "confirmed" || status === "writing" || status === "completed";

export function formatTopicPublishRange(topic: HotNewsTopic): string {
  const from = topic.publishFrom;
  const to = topic.publishTo;
  if (!from && !to) return "未知";
  if (from && to) {
    const fromLabel = formatPublishDate(from);
    const toLabel = formatPublishDate(to);
    if (fromLabel === toLabel) return fromLabel;
    return `${fromLabel} ~ ${toLabel}`;
  }
  return formatPublishDate(from || to);
}

export function displayHotTopicTitle(
  title: string,
  scoreDetails: HotNewsTopic["scoreDetails"],
): string {
  const signals = scoreDetails.signals || [];
  return signals.some((signal) => signal.includes("GitHub")) &&
    !/[\u4e00-\u9fff]/.test(title)
    ? `GitHub 项目：${title}`
    : title;
}

/** 从标题/信号判断热点形态，供速览首句使用。 */
export const TopicKind = {
  PAPER: "paper",
  GITHUB: "github",
  PRODUCT: "product",
  NEWS: "news",
  UNKNOWN: "unknown",
} as const;

export type TopicKindValue = (typeof TopicKind)[keyof typeof TopicKind];

const TOPIC_KIND_LABELS: Record<TopicKindValue, string> = {
  [TopicKind.PAPER]: "研究论文 / 评测基准",
  [TopicKind.GITHUB]: "GitHub 开源项目",
  [TopicKind.PRODUCT]: "产品 / 平台动态",
  [TopicKind.NEWS]: "资讯动态",
  [TopicKind.UNKNOWN]: "热点线索",
};

export function classifyHotTopic(topic: HotNewsTopic): {
  kind: TopicKindValue;
  kindLabel: string;
} {
  const title = topic.title || "";
  const summary = topic.summary || "";
  const signals = (topic.scoreDetails.signals || []).join(" ");
  const blob = `${title}\n${summary}\n${signals}`.toLowerCase();
  if (
    /github|仓库|开源项目/.test(blob) ||
    (/^[A-Za-z0-9_.-]+\/[A-Za-z0-9_.-]+$/.test(title.trim()) &&
      !/\s/.test(title.trim()))
  ) {
    return { kind: TopicKind.GITHUB, kindLabel: TOPIC_KIND_LABELS[TopicKind.GITHUB] };
  }
  if (
    /arxiv|benchmark|paper|survey|proceedings|conference|journal|preprint|论文|基准/.test(
      blob,
    )
  ) {
    return { kind: TopicKind.PAPER, kindLabel: TOPIC_KIND_LABELS[TopicKind.PAPER] };
  }
  if (/product|release|launch|sdk|platform|产品|发布/.test(blob)) {
    return { kind: TopicKind.PRODUCT, kindLabel: TOPIC_KIND_LABELS[TopicKind.PRODUCT] };
  }
  if (/rss|新闻|资讯|报道/.test(blob)) {
    return { kind: TopicKind.NEWS, kindLabel: TOPIC_KIND_LABELS[TopicKind.NEWS] };
  }
  return { kind: TopicKind.UNKNOWN, kindLabel: TOPIC_KIND_LABELS[TopicKind.UNKNOWN] };
}

/** 从英文标题抽几个可理解的中文线索（启发式，非完整翻译）。 */

export function chineseHintsFromTitle(title: string): string[] {
  const rules: Array<[RegExp, string]> = [
    [/multi[- ]?agent|多智能体/i, "多智能体"],
    [/benchmark|bench\b/i, "评测基准"],
    [/secur(e|ity)|安全/i, "安全评测"],
    [/llm|large language model|大语言模型/i, "大语言模型"],
    [/agent(?!ic)/i, "智能体"],
    [/rag|retrieval[- ]augment/i, "RAG / 检索增强"],
    [/decision[- ]support|决策支持/i, "决策支持"],
    [/geothermal|地热/i, "地热场景"],
    [/model(ing|ling)?/i, "建模"],
    [/evaluat|评估/i, "能力评估"],
    [/framework|框架/i, "框架"],
    [/intelligence|情报/i, "情报聚合"],
    [/recommend|推荐/i, "推荐"],
    [/verif(y|ication)|核验/i, "事实核验"],
  ];
  const hints: string[] = [];
  for (const [pattern, label] of rules) {
    if (pattern.test(title) && !hints.includes(label)) {
      hints.push(label);
    }
    if (hints.length >= 4) break;
  }
  return hints;
}

export function cleanSummaryText(text: string): string {
  return text.replace(/\s+/g, " ").trim();
}

export function truncateText(text: string, max = 180): string {
  const cleaned = cleanSummaryText(text);
  if (cleaned.length <= max) return cleaned;
  return `${cleaned.slice(0, max).replace(/[，,。.\s]+$/u, "")}…`;
}

/**
 * 事实速览：优先展示由成员信源 AI 概要汇总后的簇摘要；
 * 无摘要时再给来源形态提示。
 */

export function buildHotTopicGlance(topic: HotNewsTopic): {
  lead: string;
  excerpt: string | null;
} {
  const summary = stripArxivBoilerplate(cleanSummaryText(topic.summary || ""));
  const reason = cleanSummaryText(topic.scoreReason || "");
  const hasChineseSummary = /[\u4e00-\u9fff]/.test(summary);
  const summaryUseful =
    summary.length >= 24 &&
    summary !== cleanSummaryText(topic.title) &&
    !/^该热点的原始来源为英文内容/.test(summary)
    && !/^这是一条/.test(summary);

  if (summaryUseful && hasChineseSummary) {
    return { lead: truncateText(summary, 280), excerpt: null };
  }
  if (summaryUseful) {
    return {
      lead: `原文仍偏英文，采集后会自动提炼中文速览。暂录：${truncateText(summary, 180)}`,
      excerpt: null,
    };
  }

  const { kindLabel } = classifyHotTopic(topic);
  const hints = chineseHintsFromTitle(topic.title);
  const hintText = hints.length ? `，聚焦：${hints.join("、")}` : "";
  const sourceHint = sourceTypeLabel(topic);
  const lead = `这是一条${kindLabel}（${sourceHint}）${hintText}。`;
  if (reason.length >= 20 && reason !== summary) {
    return { lead, excerpt: `评分说明：${truncateText(reason, 180)}` };
  }
  return {
    lead,
    excerpt: "暂无可用摘要。请点「证据/简报」查看来源标题与各条 AI 概要后再决定是否选题。",
  };
}

/** 证据条目上展示的 AI 概要（note / snippet）。 */

export function evidenceFactBrief(item: HotNewsEvidence): string {
  return cleanSummaryText(item.note || item.snippet || "");
}

/** 去掉 arXiv 元数据头，避免速览被 Announce/Abstract 占满。 */

export function stripArxivBoilerplate(text: string): string {
  return text
    .replace(/^arXiv:[^\n]*\n?/i, "")
    .replace(/^Announce Type:\s*\w+\s*/i, "")
    .replace(/^Abstract:\s*/i, "")
    .replace(/\s+/g, " ")
    .trim();
}

export function topicOriginTags(topic: HotNewsTopic): string[] {
  if (isAiThemeSeedTopic(topic)) {
    return [TOPIC_ORIGIN_LABELS[TopicOriginKind.AI_THEME_SEED]];
  }
  const sites = (topic.siteLabels || []).filter(Boolean);
  const medias = (topic.mediaTypes || []).filter(Boolean);
  if (sites.length || medias.length) {
    return [...sites, ...medias];
  }
  const types = topic.scoreDetails?.sourceTypes;
  if (!types?.length) return ["公开来源"];
  return types.map((type) => channelTypeShortLabel(type));
}

export function sourceTypeLabel(topic: HotNewsTopic): string {
  return topicOriginTags(topic).join(" · ");
}

export function originTagClass(label: string): string {
  if (label === TOPIC_ORIGIN_LABELS[TopicOriginKind.AI_THEME_SEED]) {
    return "hot-origin-tag hot-origin-ai";
  }
  if (label === "视频") return "hot-origin-tag hot-media-tag hot-media-video";
  if (label === "文章") return "hot-origin-tag hot-media-tag hot-media-article";
  return "hot-origin-tag";
}

export function editorialJudgement(topic: HotNewsTopic): string {
  const recommend = topic.scoreDetails?.recommendUse;
  if (recommend === "lead") return "相对本主题适合作为主线深度写作。";
  if (recommend === "brief") return "相对本主题适合短讯/快评，材料深度有限。";
  if (recommend === "skip") return "相对本主题建议跳过或暂不投入写作。";
  if (recommend === "watch") return "相对本主题可先观察，待材料更完整再确认。";
  const risk = (topic.scoreDetails?.riskNote || "").trim();
  if (risk) return risk;
  const unknown = Number(topic.scoreDetails?.unknownDateCount || 0);
  if (unknown > 0)
    return `有 ${unknown} 个来源缺少明确发布时间，建议先核对原文时间再确认选题。`;
  return "已按主题评分量表完成写作价值判断，可结合分维明细决定是否选题。";
}

/** 卡片上展示的主题动态维度（优先 AI 分维，否则量表快照）。 */

export function topicWritingDimensions(topic: HotNewsTopic) {
  const dims = topic.scoreDetails?.dimensions;
  if (dims?.length) {
    return dims;
  }
  return topic.scoreDetails?.rubricSnapshot || [];
}

export const CHANNEL_RUN_STATUS: Record<string, string> = {
  running: "采集中",
  succeeded: "成功",
  failed: "失败",
};
export const EVIDENCE_STATUS: Record<string, string> = {
  verified: "已确认",
  multi_source: "多源支持",
  unverified: "待确认",
  contradicted: "存在冲突",
};
