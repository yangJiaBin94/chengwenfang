// @ts-nocheck
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
import {
  LIST_PAGE_SIZE_OPTIONS,
  listPageNumbers,
} from "../../../shared/api/pagination";
import { formatPublishDate } from "../../../shared/format/time";
import { withDocumentIdQuery } from "../api";
import {
  ChannelType,
  channelTypeShortLabel,
} from "../model";
import {
  formatTopicPublishRange,
  displayHotTopicTitle,
  buildHotTopicGlance,
  evidenceFactBrief,
  topicOriginTags,
  originTagClass,
  editorialJudgement,
  topicWritingDimensions,
  isConfirmedTopic,
  CHANNEL_RUN_STATUS,
  EVIDENCE_STATUS,
} from "./topicDisplay";

const RUN_STATUS: Record<string, string> = {
  pending: "等待执行",
  running: "采集中",
  succeeded: "采集成功",
  partial: "部分成功",
  failed: "采集失败",
};

/** 热点雷达页：英雄区、流程与指标。 */
export function HotNewsPageViewHeader(props: Record<string, any>) {
  const {
    articleBusy,
    articles,
    briefs,
    candidateCount,
    collectJob,
    collectMessage,
    collectProgress,
    collectRunning,
    collectStageTitle,
    coverageRate,
    detailLoading,
    detailTab,
    detailTopic,
    enterArticleWorkspace,
    evidence,
    failedChannels,
    fetching,
    filter,
    latestRun,
    listBusy,
    listPage,
    listPageCount,
    loading,
    navigate,
    openResearchPool,
    openTopicDetail,
    pageSize,
    pageSizeOpen,
    pageSizeRef,
    refresh,
    setDetailTab,
    setDetailTopic,
    setFilter,
    setListPage,
    setPageSize,
    setPageSizeOpen,
    setSortBy,
    setStatus,
    setTopicQuery,
    sortBy,
    sources,
    sourcesPath,
    startFetch,
    successChannels,
    themeId,
    themeName,
    themeSlug,
    topicCount,
    topicQuery,
    topicSources,
    topicTotal,
    topics,
  } = props;
  return (
    <>
      <div className="context-line">热点资讯模式 / {themeName}</div>
      <div className="hot-news-hero">
        <div>
          <span className="section-kicker">Hot News Radar</span>
          <h2>热点雷达</h2>
          <p>
            从多来源发现热点资讯，筛选值得深入分析的内容，再进入人工确认和文章生产。
          </p>
        </div>
        <div className="hot-news-actions">
          <button
            className="secondary-button"
            onClick={() => void refresh()}
            disabled={loading || fetching}
          >
            <RefreshCcw size={15} />
            刷新
          </button>
          <button
            className="primary-button"
            onClick={() => void startFetch()}
            disabled={fetching}
            aria-busy={collectRunning}
          >
            <Activity size={15} />
            {fetching
              ? "启动中…"
              : collectRunning
                ? collectProgress > 0
                  ? `采集中 ${collectProgress}%`
                  : "采集中…"
                : "开始采集"}
          </button>
          <button
            className="secondary-button"
            type="button"
            onClick={() =>
              navigate(
                `/modes/hot-news/themes/${encodeURIComponent(themeSlug || themeId)}/collect-execution${
                  collectJob
                    ? `?job=${encodeURIComponent(collectJob.id)}`
                    : ""
                }`,
              )
            }
          >
            采集记录
          </button>
        </div>
      </div>

      {collectRunning && collectMessage ? (
        <div
          className={`detail-task-status hot-radar-collect-status running`}
          role="status"
          aria-live="polite"
        >
          <RefreshCcw size={14} />
          <span>
            {collectMessage}
            {collectProgress > 0 ? ` · ${collectProgress}%` : ""}
          </span>
          <button
            type="button"
            onClick={() =>
              navigate(
                `/modes/hot-news/themes/${encodeURIComponent(themeSlug || themeId)}/collect-execution?job=${encodeURIComponent(collectJob!.id)}`,
              )
            }
          >
            查看轨迹
          </button>
        </div>
      ) : null}

      <section className="hot-news-flow" aria-label="热点文章生产流程">
        <div className="hot-news-flow-title">
          <span className="section-kicker">EDITORIAL FLOW</span>
          <strong>从发现到成稿</strong>
          <small>每一步都有明确产物，避免在一个页面里同时处理不同任务。</small>
        </div>
        <div className="hot-news-flow-step is-current">
          <span>01</span>
          <div>
            <strong>发现热点</strong>
            <small>多来源采集、聚合、评分</small>
          </div>
        </div>
        <i />
        <div className="hot-news-flow-step">
          <span>02</span>
          <div>
            <strong>确认选题</strong>
            <small>查看来源与评分理由</small>
          </div>
        </div>
        <i />
        <div className="hot-news-flow-step">
          <span>03</span>
          <div>
            <strong>深挖资料</strong>
            <small>形成选题资料池与简报</small>
          </div>
        </div>
        <i />
        <div className="hot-news-flow-step">
          <span>04</span>
          <div>
            <strong>生成文章</strong>
            <small>分步生成、复检、排版</small>
          </div>
        </div>
      </section>

      <div className="hot-news-metrics">
        <div>
          <span>候选选题</span>
          <strong>{candidateCount}</strong>
          <small>等待人工确认</small>
        </div>
        <div>
          <span>热点总数</span>
          <strong>{topicCount}</strong>
          <small>当日评分前 20</small>
        </div>
        <div>
          <span>启用来源</span>
          <strong>{sources.filter((item) => item.enabled).length}</strong>
          <small>当前主题</small>
        </div>
        <div>
          <span>最近采集</span>
          <strong>
            {collectRunning ? "…" : (latestRun?.newItems ?? 0)}
          </strong>
          <small>
            {collectRunning
              ? `采集中 · ${collectStageTitle || "进行中"}`
              : latestRun
                ? `${RUN_STATUS[latestRun.status] || latestRun.status} · 覆盖 ${coverageRate ?? 0}%`
                : "尚未执行"}
          </small>
        </div>
      </div>

    </>
  );
}
