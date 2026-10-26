// @ts-nocheck
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
  type HotNewsArticleSkeleton,
  type HotNewsBrief,
  type HotNewsResearchItemPage,
} from "../api";
import {
  researchItemScoreDetails,
  researchItemDimensions,
  parseVideoParseProgressMessage,
  formatParallelRunningLabel,
  asProgressTitles,
  formatParseProgressLine,
  VideoParseResultModal,
  ResearchBriefPanel,
  SkeletonModal,
} from "../components/researchPanels";

export type HotNewsResearchViewTopProps = {
  active: any,
  articleBusy: any,
  articleCount: any,
  brief: any,
  buildSkeleton: any,
  busy: any,
  conflicts: any,
  currentStep: any,
  enterWorkspace: any,
  indeterminate: any,
  itemCount: any,
  latest: any,
  latestStatus: any,
  navigate: any,
  pendingVideoCount: any,
  progress: any,
  refresh: any,
  research: any,
  statusLabels: any,
  stepLabels: any,
  selectedPath: any,
  setSkeletonOpen: any,
  skeleton: any,
  skeletonBusy: any,
  skeletonOpen: any,
  startBatchVideoParse: any,
  total: any,
  videoCount: any,
  videoParseBusy: any,
  videoParseMessage: any,
  videoParseMeta: any,
  videoParseProgress: any,
  videoParseScope: any
};

export function HotNewsResearchViewTop({
  active,
  articleBusy,
  articleCount,
  brief,
  buildSkeleton,
  busy,
  conflicts,
  currentStep,
  enterWorkspace,
  indeterminate,
  itemCount,
  latest,
  latestStatus,
  navigate,
  pendingVideoCount,
  progress,
  refresh,
  research,
  statusLabels,
  stepLabels,
  selectedPath,
  setSkeletonOpen,
  skeleton,
  skeletonBusy,
  skeletonOpen,
  startBatchVideoParse,
  total,
  videoCount,
  videoParseBusy,
  videoParseMessage,
  videoParseMeta,
  videoParseProgress,
  videoParseScope
}: HotNewsResearchViewTopProps) {
  return (
    <>
      <header className="hot-news-hero">
        <div>
          <div className="hot-research-heading-meta">
            <button
              type="button"
              className="hot-research-back"
              onClick={() => navigate(selectedPath)}
            >
              <ArrowLeft size={14} />
              返回已选题
            </button>
            <span className="section-kicker">TOPIC RESEARCH POOL</span>
          </div>
          <h2>选题资料池</h2>
          <p>
            先补充资料，再解析视频素材、拟定文章骨架，然后进入文章工作区按工作流生成。
          </p>
        </div>
        <div className="hot-news-actions">
          <button className="secondary-button" onClick={() => void refresh()}>
            <RefreshCcw size={15} />
            刷新
          </button>
          <button
            className="secondary-button"
            disabled={skeletonBusy || busy || active}
            title="根据资料池与选题，限定文章范围、核心与结构"
            onClick={() => void buildSkeleton()}
          >
            <LayoutTemplate size={15} />
            {skeletonBusy ? "拟定中..." : "拟定文章骨架"}
          </button>
          <button
            className="primary-button hot-research-generate-button"
            disabled={articleBusy}
            title={
              active || itemCount <= 0 || !skeleton?.coreThesis
                ? "资料或骨架未就绪时会先提示；也可仍进入工作区"
                : "进入工作区，按工作流生成文章"
            }
            onClick={() => void enterWorkspace()}
          >
            <FileText size={15} />
            {articleBusy ? "准备中..." : "进入文章工作区"}
          </button>
          <button
            className="primary-button"
            disabled={busy || active || videoParseBusy}
            aria-busy={active}
            title={
              videoParseBusy
                ? "视频解析进行中，请完成后再补充资料"
                : undefined
            }
            onClick={() => void research()}
          >
            <Search size={15} />
            {busy
              ? "启动中..."
              : active
                ? indeterminate
                  ? "执行中..."
                  : `执行中 ${progress}%`
                : "补充资料与核验"}
          </button>
        </div>
      </header>

      {brief || skeleton?.coreThesis ? (
        <ResearchBriefPanel
          brief={brief}
          canOpenSkeleton={Boolean(skeleton?.coreThesis)}
          onOpenSkeleton={() => setSkeletonOpen(true)}
        />
      ) : null}
      {skeletonOpen && skeleton?.coreThesis ? (
        <SkeletonModal skeleton={skeleton} onClose={() => setSkeletonOpen(false)} />
      ) : null}

      <section className={`hot-research-status ${active || videoParseBusy ? "is-active" : ""}`}>
        <div className="hot-research-status-head">
          <strong>{statusLabels[latestStatus] || "尚未补充"}</strong>
          <span>
            {latest
              ? `本次整理 ${itemCount} 条资料`
              : "点击「补充资料与核验」开始检索"}
          </span>
        </div>
        {active ? (
          <div className="hot-research-progress-wrap">
            <div
              className={`hot-research-progress${indeterminate ? " is-indeterminate" : ""}`}
              role="progressbar"
              aria-valuenow={indeterminate ? undefined : progress}
              aria-valuemin={0}
              aria-valuemax={100}
            >
              <i style={indeterminate ? undefined : { width: `${Math.max(4, progress)}%` }} />
            </div>
            <small>
              {stepLabels[currentStep] || currentStep || "任务执行中"}
              {indeterminate ? "" : ` · ${progress}%`}
            </small>
          </div>
        ) : null}
        <div className="hot-research-counts">
          <span>资料 {itemCount} 条</span>
          <span>文章 {articleCount} 条</span>
          <span>视频 {videoCount} 条</span>
          <span>冲突 {conflicts.length} 条</span>
        </div>
        {videoCount > 0 ? (
          <p className="hot-research-video-tip">
            视频素材需解析后才可作为成文参考（字幕优先，否则语音识别，再经 AI 总结要点）。
          </p>
        ) : null}
        {videoCount > 0 ? (
          <div className="hot-research-video-actions">
            <button
              className="primary-button"
              disabled={videoParseBusy || pendingVideoCount === 0 || active}
              onClick={() => void startBatchVideoParse()}
            >
              <Clapperboard size={15} />
              {videoParseBusy && videoParseScope === "batch"
                ? "解析进行中…"
                : pendingVideoCount > 0
                  ? `解析 ${pendingVideoCount} 条视频素材`
                  : "视频均已解析"}
            </button>
          </div>
        ) : null}
        {videoParseBusy && videoParseScope === "batch" ? (
          <div className="hot-research-progress-wrap hot-research-batch-progress">
            <div
              className="hot-research-progress"
              role="progressbar"
              aria-valuenow={videoParseProgress}
              aria-valuemin={0}
              aria-valuemax={100}
            >
              <i style={{ width: `${Math.max(4, videoParseProgress)}%` }} />
            </div>
            <div className="hot-research-batch-progress-text">
              <strong>
                共 {videoParseMeta.total ?? pendingVideoCount ?? "—"} 条
                {" · "}
                已完成 {videoParseMeta.completed ?? 0} 条
                {" · "}
                {formatParallelRunningLabel(videoParseMeta.running ?? 0)}
                {` · ${Math.max(0, Math.min(100, Math.round(videoParseProgress)))}%`}
              </strong>
              {(videoParseMeta.titles?.length ?? 0) > 0 ? (
                <span className="hot-research-batch-progress-title">
                  进行中：{(videoParseMeta.titles || []).slice(0, 5).join("；")}
                  {(videoParseMeta.titles?.length ?? 0) > 5
                    ? ` 等 ${videoParseMeta.titles!.length} 条`
                    : ""}
                </span>
              ) : (
                <span className="hot-research-batch-progress-title">
                  {videoParseMessage || "等待线程池调度…"}
                </span>
              )}
              <small>多路并行解析中，各条目状态见下方列表</small>
            </div>
          </div>
        ) : null}
        {latest?.errorMessage ? (
          <em>
            <AlertTriangle size={14} />
            {String(latest?.errorMessage)}
          </em>
        ) : null}
      </section>

    </>
  );
}
