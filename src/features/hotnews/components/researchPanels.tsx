import { useEffect, useRef, useState } from "react";
import {
  AlertTriangle,
  ArrowLeft,
  Check,
  ChevronDown,
  ChevronLeft,
  ChevronRight,
  Clapperboard,
  ExternalLink,
  Eye,
  FileText,
  LayoutTemplate,
  Link2,
  Plus,
  RefreshCcw,
  Search,
  X,
} from "lucide-react";
import { loadJob } from "../../../shared/api/jobs";
import { formatVideoDuration } from "../../../shared/format/duration";
import { formatLocaleDateTime } from "../../../shared/format/time";
import { usePolling } from "../../../shared/hooks/usePolling";
import {
  LIST_PAGE_SIZE_OPTIONS,
  listPageNumbers,
  type ListPageSize,
} from "../../../shared/api/pagination";
import {
  addHotNewsManualResearchItem,
  buildHotNewsArticleSkeleton,
  ensureHotNewsArticle,
  getHotNewsArticleSkeleton,
  listHotNewsArticles,
  listHotNewsBriefs,
  listHotNewsResearchConflicts,
  listHotNewsResearchRuns,
  pageHotNewsResearchItems,
  startHotNewsTopicResearch,
  startHotNewsVideoParse,
  startHotNewsVideoParseItem,
  withDocumentIdQuery,
  type HotNewsArticle,
  type HotNewsBrief,
  type HotNewsResearchItemPage,
} from "../api";
import { SkeletonModal } from "../../../shared/components/SkeletonModal";
import type { ResearchItemScoreDetails, TopicDimensionScore } from "../model";
import { text } from "./hotNewsExecutionHelpers";

export { SkeletonModal };

export const PARSE_STATUS_LABELS: Record<string, string> = {
  none: "未解析",
  running: "解析中",
  succeeded: "已解析",
  failed: "解析失败",
};

export const RESEARCH_RECOMMEND_LABELS: Record<string, string> = {
  priority: "优先素材",
  supplement: "可作为补充",
  cautious: "谨慎使用",
};

export const VIDEO_STEP_LABELS: Record<string, string> = {
  probe_duration: "检查时长",
  fetch_subtitle: "提取字幕",
  download_audio: "下载音频",
  convert_format: "转换格式",
  asr: "语音识别",
  download_video: "下载视频",
  extract_frames: "提取关键帧",
  vision_summarize: "画面理解",
  summarize: "总结内容",
  done: "已完成",
};

export function researchItemScoreDetails(
  item: Record<string, unknown>,
): ResearchItemScoreDetails | null {
  const raw = item.scoreDetails;
  if (!raw || typeof raw !== "object") return null;
  return raw as ResearchItemScoreDetails;
}

/** 评分明细中的分维列表。 */

export function researchItemDimensions(
  details: ResearchItemScoreDetails | null,
): TopicDimensionScore[] {
  return details?.dimensions?.filter((dim) => dim && dim.key) || [];
}

/** 将秒数格式化为 m:ss 或 h:mm:ss。 */

export function parseVideoParseProgressMessage(message: string): {
  total?: number;
  completed?: number;
  running?: number;
  titles?: string[];
} {
  const text = String(message || "").trim();
  if (!text) return {};
  // 新格式：共 N 条 · 已完成 C 条 · 并行解析中 R 条 · P%
  const parallel = text.match(
    /共\s*(\d+)\s*条\s*·\s*已完成\s*(\d+)\s*条\s*·\s*(?:并行解析中|进行中)\s*(\d+)\s*条/,
  );
  if (parallel) {
    return {
      total: Number(parallel[1]),
      completed: Number(parallel[2]),
      running: Number(parallel[3]),
    };
  }
  const waiting = text.match(
    /共\s*(\d+)\s*条\s*·\s*已完成\s*(\d+)\s*条\s*·\s*(排队等待中|即将结束)/,
  );
  if (waiting) {
    return {
      total: Number(waiting[1]),
      completed: Number(waiting[2]),
      running: 0,
    };
  }
  // 兼容旧串行文案
  const legacy = text.match(
    /共\s*(\d+)\s*条\s*·\s*已完成\s*(\d+)\s*条\s*·\s*正在解析第\s*(\d+)\s*条/,
  );
  if (!legacy) return {};
  return {
    total: Number(legacy[1]),
    completed: Number(legacy[2]),
    running: 1,
  };
}

/** 进行中文案：0/1/多路。 */

export function formatParallelRunningLabel(running: number): string {
  if (running <= 0) return "排队等待中";
  if (running === 1) return "进行中 1 条";
  return `并行解析中 ${running} 条`;
}

/** 解析进度 meta 中的进行中标题列表。 */

export function asProgressTitles(
  raw: unknown,
  fallback?: string[],
): string[] {
  if (Array.isArray(raw)) {
    return raw
      .map((item) => String(item ?? "").trim())
      .filter(Boolean);
  }
  return fallback ?? [];
}

/** 拼进度文案：步骤名只出现一次，可选附加说明与百分比。 */

export function formatParseProgressLine(
  stepKey: string,
  message: string,
  progress: number | null,
): string {
  const stepLabel = VIDEO_STEP_LABELS[stepKey] || stepKey || "解析中";
  const extra = String(message || "").trim();
  const parts = [stepLabel];
  if (extra && extra !== stepLabel && extra !== stepKey) {
    parts.push(extra);
  }
  if (progress != null) {
    parts.push(`${Math.max(0, Math.min(100, Math.round(progress)))}%`);
  }
  return parts.join(" · ");
}

const BRIEF_STATUS: Record<string, string> = {
  draft: "草稿",
  confirmed: "已确认",
};

export function asTextList(value: unknown): string[] {
  if (!Array.isArray(value)) return [];
  return value.map((item) => String(item ?? "").trim()).filter(Boolean);
}

export function VideoParseResultModal({
  item,
  onClose,
}: {
  item: Record<string, unknown>;
  onClose: () => void;
}) {
  const summary = String(item.contentSummary || item.summary || "").trim();
  const raw = String(item.videoRawText || "").trim();
  const isVisual = String(item.videoContentKind || "") === "visual";
  return (
    <div className="modal-backdrop" onMouseDown={onClose}>
      <section
        className="modal-card hot-research-skeleton-modal"
        onMouseDown={(event) => event.stopPropagation()}
      >
        <header className="modal-header">
          <div>
            <span className="section-kicker">
              视频解析结果{isVisual ? " · 画面解析" : ""}
            </span>
            <h3>{String(item.title || "未命名")}</h3>
          </div>
          <button className="icon-button" onClick={onClose} aria-label="关闭">
            <X size={18} />
          </button>
        </header>
        <div className="hot-research-skeleton">
          <p>
            <strong>AI 总结要点</strong>（成文参考）
          </p>
          <p style={{ whiteSpace: "pre-wrap" }}>
            {summary || "暂无总结内容"}
          </p>
          {!isVisual && raw ? (
            <details className="hot-research-raw-details">
              <summary>原始字幕 / 识别稿</summary>
              <pre>{raw}</pre>
            </details>
          ) : null}
        </div>
        <div className="modal-actions">
          <button className="primary-button" onClick={onClose}>
            关闭
          </button>
        </div>
      </section>
    </div>
  );
}

export function ResearchBriefPanel({
  brief,
  canOpenSkeleton,
  onOpenSkeleton,
}: {
  brief: HotNewsBrief | null;
  canOpenSkeleton: boolean;
  onOpenSkeleton: () => void;
}) {
  const summary = brief?.summary ?? {};
  const overview = String(summary.overview || brief?.angle || "").trim();
  const coreThesis = String(summary.coreThesis || "").trim();
  const keyPoints = asTextList(summary.keyPoints);
  const openQuestions = asTextList(summary.openQuestions);
  return (
    <section className="hot-research-brief">
      <div className="hot-research-brief-head">
        <strong>选题简报</strong>
        {canOpenSkeleton ? (
          <button
            type="button"
            className="secondary-button hot-research-brief-open"
            onClick={onOpenSkeleton}
          >
            查看文章骨架
          </button>
        ) : null}
      </div>
      {brief?.title ? <h3>{brief.title}</h3> : null}
      {overview ? <p>{overview}</p> : null}
      {coreThesis ? (
        <p>
          <b>核心论点：</b>
          {coreThesis}
        </p>
      ) : null}
      {keyPoints.length > 0 ? (
        <div className="hot-research-brief-list">
          <b>要点</b>
          <ul>
            {keyPoints.map((point) => (
              <li key={point}>{point}</li>
            ))}
          </ul>
        </div>
      ) : null}
      {openQuestions.length > 0 ? (
        <div className="hot-research-brief-list">
          <b>待核实</b>
          <ul>
            {openQuestions.map((point) => (
              <li key={point}>{point}</li>
            ))}
          </ul>
        </div>
      ) : null}
      {!overview && !coreThesis && keyPoints.length === 0 ? (
        <p className="hot-research-brief-empty">简报内容待补充。</p>
      ) : null}
      <div className="hot-research-brief-meta">
        {brief?.status ? (
          <span>状态：{BRIEF_STATUS[brief.status] || brief.status}</span>
        ) : null}
      </div>
    </section>
  );
}
