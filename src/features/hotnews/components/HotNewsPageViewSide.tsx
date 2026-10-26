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

/** 热点雷达页：来源与运行侧栏。 */
export function HotNewsPageViewSide(props: Record<string, any>) {
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
        <aside className="hot-news-side">
          <div className="hot-news-side-head">
            <div>
              <h3>来源渠道</h3>
              <p>来源失败不会阻塞其他来源。</p>
            </div>
            <button
              className="secondary-button hot-source-manage-button"
              type="button"
              onClick={() => navigate(sourcesPath)}
            >
              管理来源
            </button>
          </div>
          {sources.length === 0 ? (
            <div className="hot-source-empty">
              <Globe2 size={19} />
              <span>还没有配置来源</span>
              <button
                className="secondary-button"
                onClick={() => navigate(sourcesPath)}
              >
                前往来源管理
              </button>
            </div>
          ) : (
            <div className="hot-source-list hot-source-list-preview">
              {sources.slice(0, 3).map((source) => (
                <div className="hot-source-row" key={source.id}>
                  <span className="hot-source-icon">
                    {source.type === ChannelType.RSS ? (
                      <Rss size={15} />
                    ) : source.type === ChannelType.GITHUB ? (
                      <Activity size={15} />
                    ) : source.type === ChannelType.SEARXNG || source.type === ChannelType.TAVILY ? (
                      <Search size={15} />
                    ) : (
                      <Globe2 size={15} />
                    )}
                  </span>
                  <div>
                    <strong>{source.name}</strong>
                    <small>
                      {source.type.toUpperCase()} ·{" "}
                      {source.enabled ? "已启用" : "已停用"}
                    </small>
                  </div>
                </div>
              ))}
              {sources.length > 3 && (
                <button
                  className="hot-source-more"
                  type="button"
                  onClick={() => navigate(sourcesPath)}
                >
                  还有 {sources.length - 3} 个来源，前往管理
                </button>
              )}
            </div>
          )}
          <div className="hot-run-summary">
            <span>最近采集</span>
            <strong>
              {latestRun
                ? RUN_STATUS[latestRun.status] || latestRun.status
                : "尚未执行"}
            </strong>
            {latestRun && (
              <small>
                新增 {latestRun.newItems} · 去重 {latestRun.dedupedItems} · 成功{" "}
                {successChannels} / 失败 {failedChannels}
              </small>
            )}
            {latestRun?.errorSummary && <em>{latestRun.errorSummary}</em>}
          </div>
          {latestRun?.channelRuns.length ? (
            <details className="hot-run-diagnostics">
              <summary>查看渠道采集明细</summary>
              <div className="hot-run-channel-list">
                {latestRun.channelRuns.map((item) => (
                  <div
                    className={`hot-run-channel hot-run-channel-${item.status}`}
                    key={item.id}
                  >
                    <div>
                      <strong>{item.channelName}</strong>
                      <small>
                        {item.channelType.toUpperCase()} ·{" "}
                        {CHANNEL_RUN_STATUS[item.status] || item.status}
                      </small>
                    </div>
                    <span>
                      {item.newItems}/{item.itemsCount}
                    </span>
                    {item.errorMessage && <em>{item.errorMessage}</em>}
                  </div>
                ))}
              </div>
            </details>
          ) : null}
        </aside>

    </>
  );
}
