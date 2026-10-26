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

/** 热点雷达页：选题列表主栏。 */
export function HotNewsPageViewMain(props: Record<string, any>) {
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
        <div className="hot-news-main">
          <div className="hot-news-section-head">
            <div className="hot-news-section-title">
              <h3>热点雷达</h3>
              <p>
                {filter === "today"
                  ? "今天采集的选题按写作价值排序；未确认隔日清除。"
                  : "先看热度和写作价值，再决定是否进入深度写作。"}
              </p>
            </div>
            <div className="hot-news-section-toolbar">
              <div className="hot-news-filters">
                <button
                  className={filter === "today" ? "active" : ""}
                  onClick={() => {
                    setFilter("today");
                    setListPage(1);
                  }}
                >
                  今日
                </button>
                <button
                  className={filter === "all" ? "active" : ""}
                  onClick={() => {
                    setFilter("all");
                    setListPage(1);
                  }}
                >
                  全部
                </button>
                <button
                  className={filter === "candidate" ? "active" : ""}
                  onClick={() => {
                    setFilter("candidate");
                    setListPage(1);
                  }}
                >
                  待选题
                </button>
                <button
                  className={filter === "confirmed" ? "active" : ""}
                  onClick={() => {
                    setFilter("confirmed");
                    setListPage(1);
                  }}
                >
                  已确认
                </button>
              </div>
              <div className="hot-news-section-tools">
                <label className="hot-news-sort">
                  排序
                  <select
                    value={sortBy}
                    onChange={(event) => {
                      setSortBy(event.target.value as typeof sortBy);
                      setListPage(1);
                    }}
                  >
                    <option value="writing">写作价值</option>
                    <option value="heat">热度</option>
                  </select>
                </label>
                <label className="hot-topic-search">
                  <Search size={14} />
                  <input
                    value={topicQuery}
                    onChange={(event) => setTopicQuery(event.target.value)}
                    placeholder="搜索标题、摘要或来源"
                  />
                </label>
              </div>
            </div>
          </div>
          {listBusy ? (
            <div className="hot-news-empty">
              <RefreshCcw size={20} />
              <strong>正在读取热点数据</strong>
            </div>
          ) : topicTotal === 0 ? (
            <div className="hot-news-empty">
              <Search size={20} />
              <strong>
                {filter === "confirmed"
                  ? "今天还没有已确认选题"
                  : "还没有热点数据"}
              </strong>
              <span>
                {filter === "confirmed"
                  ? "确认选题后会出现在这里；往日已确认请到「已选题」。"
                  : "点击「开始采集」；即使暂无外链渠道也会用 AI 主题素材补全至 10 条。"}
              </span>
            </div>
          ) : (
            <div className="hot-topic-list-wrap">
              <div className="hot-topic-list">
                {topics.map((topic) => (
                  <article className="hot-topic-card" key={topic.id}>
                    <div className="hot-topic-head">
                      <div>
                        <div className="hot-origin-tags">
                          {topicOriginTags(topic).map((label) => (
                            <span
                              key={`${topic.id}-${label}`}
                              className={originTagClass(label)}
                            >
                              {label}
                            </span>
                          ))}
                        </div>
                        <h4>
                          {displayHotTopicTitle(topic.title, topic.scoreDetails)}
                        </h4>
                      </div>
                      <div className="hot-topic-score">
                        <strong>{topic.writingScore}</strong>
                        <small>写作价值</small>
                      </div>
                    </div>
                    <div className="hot-topic-summary">
                      <strong>事实速览</strong>
                      {(() => {
                        const glance = buildHotTopicGlance(topic);
                        return (
                          <>
                            <p className="hot-topic-glance-lead">{glance.lead}</p>
                            {glance.excerpt ? (
                              <p className="hot-topic-glance-excerpt">{glance.excerpt}</p>
                            ) : null}
                          </>
                        );
                      })()}
                    </div>
                    <div className="hot-topic-meta">
                      <span>热度 {topic.heatScore}</span>
                      <span>可信度 {topic.confidenceScore}</span>
                      <span>{topic.sourceCount} 个来源</span>
                      <span>日期 {formatTopicPublishRange(topic)}</span>
                    </div>
                    <details className="hot-topic-scoring">
                      <summary>
                        <span>评分依据</span>
                        <span className="hot-topic-scoring-toggle">
                          <span className="when-closed">展开详情</span>
                          <span className="when-open">收起详情</span>
                          <ChevronDown size={15} />
                        </span>
                      </summary>
                      <div className="hot-topic-score-grid">
                        {topicWritingDimensions(topic).length ? (
                          topicWritingDimensions(topic).map((dim) => (
                            <span key={dim.key || dim.label}>
                              <strong>
                                {dim.score == null ? "-" : dim.score}
                              </strong>
                              {dim.label || dim.key || "维度"}
                              {dim.weight != null ? (
                                <em className="hot-dim-weight">{dim.weight}%</em>
                              ) : null}
                            </span>
                          ))
                        ) : (
                          <span>
                            <strong>-</strong>待按主题量表评分
                          </span>
                        )}
                      </div>
                      {topicWritingDimensions(topic).some((dim) => dim.reason) ? (
                        <div className="hot-topic-dim-reasons">
                          {topicWritingDimensions(topic)
                            .filter((dim) => (dim.reason || "").trim())
                            .slice(0, 6)
                            .map((dim) => (
                              <small key={`reason-${dim.key || dim.label}`}>
                                {dim.label || dim.key}：{dim.reason}
                              </small>
                            ))}
                        </div>
                      ) : null}
                      {topic.scoreDetails.signals?.length ? (
                        <div className="hot-topic-signals">
                          {topic.scoreDetails.signals
                            .slice(0, 4)
                            .map((signal) => (
                              <span key={signal}>{signal}</span>
                            ))}
                        </div>
                      ) : null}
                      <div className="hot-topic-reason">
                        <strong>编辑判断</strong>
                        <span>{editorialJudgement(topic)}</span>
                        <small>{topic.scoreReason}</small>
                        {topic.scoreDetails.riskNote ? (
                          <small>风险：{topic.scoreDetails.riskNote}</small>
                        ) : null}
                      </div>
                    </details>
                    <div className="hot-topic-actions">
                      <button
                        className="secondary-button"
                        onClick={() => void openTopicDetail(topic)}
                      >
                        <FileText size={14} />
                        证据/简报
                      </button>
                      <button
                        className="secondary-button"
                        disabled={topic.status === "ignored"}
                        onClick={() => void setStatus(topic, "ignored")}
                      >
                        <X size={14} />
                        忽略
                      </button>
                      <button
                        className="primary-button"
                        disabled={isConfirmedTopic(topic.status)}
                        onClick={() => void setStatus(topic, "confirmed")}
                      >
                        <CheckCircle2 size={14} />
                        {isConfirmedTopic(topic.status) ? "已确认" : "确认选题"}
                      </button>
                    </div>
                  </article>
                ))}
              </div>
              {topicTotal > 0 ? (
                <nav className="source-management-pager hot-topic-pager" aria-label="热点分页">
                  <span className="source-management-pager-total">共 {topicTotal} 条</span>
                  <div className="source-management-pager-controls">
                    <button
                      type="button"
                      className="source-management-pager-nav"
                      disabled={listPage <= 1 || listBusy}
                      aria-label="上一页"
                      onClick={() => setListPage((page) => Math.max(1, page - 1))}
                    >
                      <ChevronLeft size={16} />
                      上一页
                    </button>
                    {listPageNumbers(listPage, listPageCount).map((token, index) =>
                      token === "ellipsis" ? (
                        <span
                          className="source-management-pager-ellipsis"
                          key={`ellipsis-${index}`}
                        >
                          …
                        </span>
                      ) : (
                        <button
                          type="button"
                          key={token}
                          className={`source-management-pager-page${token === listPage ? " active" : ""}`}
                          disabled={listBusy}
                          aria-current={token === listPage ? "page" : undefined}
                          aria-label={`第 ${token} 页`}
                          onClick={() => setListPage(token)}
                        >
                          {token}
                        </button>
                      ),
                    )}
                    <button
                      type="button"
                      className="source-management-pager-nav"
                      disabled={listPage >= listPageCount || listBusy}
                      aria-label="下一页"
                      onClick={() =>
                        setListPage((page) => Math.min(listPageCount, page + 1))
                      }
                    >
                      下一页
                      <ChevronRight size={16} />
                    </button>
                  </div>
                  <div className="source-management-pager-size" ref={pageSizeRef}>
                    <span>每页</span>
                    <button
                      type="button"
                      className={`source-management-pager-size-trigger${pageSizeOpen ? " open" : ""}`}
                      disabled={listBusy}
                      aria-label="每页条数"
                      aria-haspopup="listbox"
                      aria-expanded={pageSizeOpen}
                      onClick={() => setPageSizeOpen((open) => !open)}
                    >
                      <em>{pageSize} 条</em>
                      <ChevronDown size={14} />
                    </button>
                    {pageSizeOpen ? (
                      <ul
                        className="source-management-pager-size-menu"
                        role="listbox"
                        aria-label="可选每页条数"
                      >
                        {LIST_PAGE_SIZE_OPTIONS.map((size) => {
                          const active = size === pageSize;
                          return (
                            <li key={size} role="option" aria-selected={active}>
                              <button
                                type="button"
                                className={active ? "active" : undefined}
                                onClick={() => {
                                  setPageSize(size);
                                  setListPage(1);
                                  setPageSizeOpen(false);
                                }}
                              >
                                <span>{size} 条</span>
                                {active ? <Check size={14} /> : null}
                              </button>
                            </li>
                          );
                        })}
                      </ul>
                    ) : null}
                  </div>
                </nav>
              ) : null}
            </div>
          )}
        </div>

    </>
  );
}
