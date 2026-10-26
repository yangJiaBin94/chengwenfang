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

/** 热点雷达页：选题详情弹层。 */
export function HotNewsPageViewDetail(props: Record<string, any>) {
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
      {detailTopic && (
        <div
          className="modal-backdrop"
          role="presentation"
          onMouseDown={() => setDetailTopic(null)}
        >
          <div
            className="modal-card hot-brief-modal"
            role="dialog"
            aria-modal="true"
            onMouseDown={(event) => event.stopPropagation()}
          >
            <div className="modal-header">
              <div>
                <span className="section-kicker">Topic Brief</span>
                <h3>{detailTopic.title}</h3>
              </div>
              <button
                className="icon-button"
                type="button"
                aria-label="关闭"
                onClick={() => setDetailTopic(null)}
              >
                <X size={16} />
              </button>
            </div>
            {detailLoading ? (
              <div className="hot-brief-loading">
                <RefreshCcw size={18} />
                正在读取选题证据
              </div>
            ) : (
              <div className="hot-detail-content">
                <nav className="hot-detail-tabs" aria-label="选题详情分区">
                  <button
                    className={detailTab === "evidence" ? "active" : ""}
                    onClick={() => setDetailTab("evidence")}
                  >
                    <ShieldCheck size={14} />
                    资料与证据<span>{evidence.length}</span>
                  </button>
                  <button
                    className={detailTab === "brief" ? "active" : ""}
                    onClick={() => setDetailTab("brief")}
                  >
                    <FileText size={14} />
                    选题简报<span>{briefs.length}</span>
                  </button>
                  <button
                    className={detailTab === "article" ? "active" : ""}
                    onClick={() => setDetailTab("article")}
                  >
                    <FileText size={14} />
                    文章结果<span>{articles.length}</span>
                  </button>
                </nav>
                <div className={`hot-brief-body hot-detail-tab-${detailTab}`}>
                  <section className="hot-brief-panel hot-brief-evidence-panel">
                    <div className="hot-brief-section-title">
                      <ShieldCheck size={15} />
                      来源证据
                    </div>
                    {topicSources.length ? (
                      <div className="hot-brief-source-chips">
                        {topicSources.slice(0, 5).map((source) => (
                          <a
                            href={source.url}
                            target="_blank"
                            rel="noreferrer"
                            key={source.id}
                          >
                            {source.siteLabel || source.sourceName}
                            {source.mediaType === "video"
                              ? " · 视频"
                              : source.mediaType === "article"
                                ? " · 文章"
                                : ""}
                            {" · "}
                            {formatPublishDate(source.publishedAt)}
                          </a>
                        ))}
                        {topicSources.length > 5 && (
                          <span>+{topicSources.length - 5}</span>
                        )}
                      </div>
                    ) : null}
                    <div className="hot-evidence-list">
                      {evidence.length === 0 && topicSources.length === 0 ? (
                        <p>暂无证据记录，请先确认选题并生成简报。</p>
                      ) : evidence.length === 0 ? (
                        topicSources.map((source) => (
                          <article
                            className="hot-evidence-row hot-evidence-auto"
                            key={source.id}
                          >
                            <div>
                              <strong>{source.title}</strong>
                              <small>
                                {source.siteLabel || source.sourceName || "未知来源"}
                                {source.mediaType === "video"
                                  ? " · 视频"
                                  : source.mediaType === "article"
                                    ? " · 文章"
                                    : ""}
                                {" · 日期 "}
                                {formatPublishDate(source.publishedAt)}
                              </small>
                              {source.summary ? (
                                <p className="hot-evidence-brief">{source.summary}</p>
                              ) : null}
                            </div>
                            {source.url ? (
                              <a
                                href={source.url}
                                target="_blank"
                                rel="noreferrer"
                              >
                                <ExternalLink size={13} />
                                来源
                              </a>
                            ) : null}
                          </article>
                        ))
                      ) : (
                        evidence.map((item) => {
                          const factBrief = evidenceFactBrief(item);
                          return (
                          <article
                            className={`hot-evidence-row hot-evidence-${item.status}`}
                            key={item.id}
                          >
                            <div>
                              <strong>{item.sourceTitle || item.statement}</strong>
                              <small>
                                {item.siteLabel || item.sourceName || "未知来源"}
                                {item.mediaType === "video"
                                  ? " · 视频"
                                  : item.mediaType === "article"
                                    ? " · 文章"
                                    : ""}
                                {" · 日期 "}
                                {formatPublishDate(item.publishedAt)}
                                {" · "}
                                {EVIDENCE_STATUS[item.status] || item.status}
                              </small>
                              {factBrief ? (
                                <p className="hot-evidence-brief">{factBrief}</p>
                              ) : (
                                <p className="hot-evidence-brief muted">
                                  暂无 AI 概要（未解析或解析失败）
                                </p>
                              )}
                            </div>
                            {item.sourceUrl && (
                              <a
                                href={item.sourceUrl}
                                target="_blank"
                                rel="noreferrer"
                              >
                                <ExternalLink size={13} />
                                来源
                              </a>
                            )}
                          </article>
                          );
                        })
                      )}
                    </div>
                  </section>
                  <section className="hot-brief-panel hot-brief-summary-panel">
                    <div className="hot-brief-section-title">
                      <FileText size={15} />
                      选题简报
                    </div>
                    {briefs.length === 0 ? (
                      <div className="hot-brief-empty">
                        <strong>简报已弱化</strong>
                        <span>
                          请到选题资料池补充资料、拟定文章骨架后生成公众号文章。
                        </span>
                      </div>
                    ) : (
                      briefs.map((brief) => (
                        <article className="hot-brief-card" key={brief.id}>
                          <div className="hot-brief-card-head">
                            <div>
                              <strong>{brief.articleType}</strong>
                              <small>{brief.status}</small>
                            </div>
                            <span>
                              {brief.sourceSnapshot.length} 个来源快照
                            </span>
                          </div>
                          <p>{brief.summary.overview || brief.title}</p>
                        </article>
                      ))
                    )}
                  </section>
                  <section className="hot-brief-panel hot-brief-article-panel">
                    <div className="hot-brief-section-title">
                      <FileText size={15} />
                      公众号文章
                    </div>
                    {articles.length === 0 ? (
                      <div className="hot-brief-empty">
                        <strong>还没有生成文章</strong>
                        <span>
                          可在资料池拟定骨架后进入工作区生成。
                        </span>
                        {detailTopic ? (
                          <button
                            className="primary-button"
                            disabled={articleBusy}
                            onClick={() => void enterArticleWorkspace(detailTopic)}
                          >
                            <FileText size={14} />
                            {articleBusy ? "准备中..." : "进入文章工作区"}
                          </button>
                        ) : null}
                      </div>
                    ) : (
                      <div className="hot-article-list">
                        {articles.map((article) => (
                          <article className="hot-article-row" key={article.id}>
                            <div>
                              <strong>{article.title}</strong>
                              <small>
                                V{article.currentVersionNo} ·{" "}
                                {article.wordCount} 字 ·{" "}
                                {article.status === "ready"
                                  ? "已生成"
                                  : article.status}
                              </small>
                            </div>
                            <p>{article.content.slice(0, 180)}...</p>
                            <button
                              className="secondary-button"
                              type="button"
                              onClick={() =>
                                navigate(
                                  withDocumentIdQuery(
                                    `/modes/hot-news/topics/${article.topicId}/articles/${article.id}`,
                                    article.documentId,
                                  ),
                                )
                              }
                            >
                              <Maximize2 size={14} />
                              进入文章工作区
                            </button>
                          </article>
                        ))}
                      </div>
                    )}
                  </section>
                </div>
              </div>
            )}
            <div className="modal-actions">
              <button
                className="secondary-button"
                onClick={() => setDetailTopic(null)}
              >
                关闭
              </button>
              <button
                className="secondary-button"
                disabled={
                  detailTopic.status !== "confirmed" &&
                  detailTopic.status !== "writing"
                }
                onClick={() => void openResearchPool(detailTopic)}
              >
                <Search size={14} />
                进入选题资料池
              </button>
              <button
                className="primary-button"
                disabled={articleBusy}
                onClick={() => void enterArticleWorkspace(detailTopic)}
              >
                <FileText size={14} />
                {articleBusy ? "准备中..." : "进入文章工作区"}
              </button>
            </div>
          </div>
        </div>
      )}

    </>
  );
}
