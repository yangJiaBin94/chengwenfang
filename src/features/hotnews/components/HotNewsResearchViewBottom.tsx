// @ts-nocheck
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
import { formatVideoDuration } from "../../../shared/format/duration";
import { formatLocaleDateTime } from "../../../shared/format/time";
import {
  LIST_PAGE_SIZE_OPTIONS,
  listPageNumbers,
  type ListPageSize,
} from "../../../shared/api/pagination";
import {
  researchItemScoreDetails,
  researchItemDimensions,
  parseVideoParseProgressMessage,
  formatParallelRunningLabel,
  asProgressTitles,
  formatParseProgressLine,
  PARSE_STATUS_LABELS,
  RESEARCH_RECOMMEND_LABELS,
  VIDEO_STEP_LABELS,
  VideoParseResultModal,
  ResearchBriefPanel,
  SkeletonModal,
} from "../components/researchPanels";

export type HotNewsResearchViewBottomProps = {
  active: any,
  conflicts: any,
  friendlyParseError: any,
  itemCount: any,
  items: any,
  listLoading: any,
  listPage: any,
  listPageCount: any,
  manualBusy: any,
  manualOpen: any,
  manualUrl: any,
  mediaFilter: any,
  pageSize: any,
  pageSizeOpen: any,
  pageSizeRef: any,
  pendingVideoCount: any,
  progress: any,
  query: any,
  research: any,
  resultItem: any,
  setListPage: any,
  setManualOpen: any,
  setManualUrl: any,
  setMediaFilter: any,
  setPageSize: any,
  setPageSizeOpen: any,
  setQuery: any,
  setResultItem: any,
  startBatchVideoParse: any,
  startItemVideoParse: any,
  submitManualItem: any,
  total: any,
  videoParseBusy: any,
  videoParseItemId: any,
  videoParseMessage: any,
  videoParseProgress: any,
  videoParseScope: any,
  videoParseStep: any
};

export function HotNewsResearchViewBottom({
  active,
  conflicts,
  friendlyParseError,
  itemCount,
  items,
  listLoading,
  listPage,
  listPageCount,
  manualBusy,
  manualOpen,
  manualUrl,
  mediaFilter,
  pageSize,
  pageSizeOpen,
  pageSizeRef,
  pendingVideoCount,
  progress,
  query,
  research,
  resultItem,
  setListPage,
  setManualOpen,
  setManualUrl,
  setMediaFilter,
  setPageSize,
  setPageSizeOpen,
  setQuery,
  setResultItem,
  startBatchVideoParse,
  startItemVideoParse,
  submitManualItem,
  total,
  videoParseBusy,
  videoParseItemId,
  videoParseMessage,
  videoParseProgress,
  videoParseScope,
  videoParseStep
}: HotNewsResearchViewBottomProps) {
  return (
    <>
      <div className="hot-research-toolbar">
        <label className="hot-topic-search">
          <Search size={14} />
          <input
            value={query}
            onChange={(event) => setQuery(event.target.value)}
            placeholder="搜索资料标题、摘要或来源"
          />
        </label>
        <select
          value={mediaFilter}
          onChange={(event) =>
            setMediaFilter(event.target.value as "all" | "article" | "video")
          }
        >
          <option value="all">全部类型</option>
          <option value="article">文章类型资源</option>
          <option value="video">视频类型资源</option>
        </select>
        <button
          className="secondary-button"
          disabled={manualBusy || active}
          onClick={() => setManualOpen(true)}
        >
          <Plus size={14} />
          添加素材
        </button>
        {pendingVideoCount > 0 ? (
          <button
            className="secondary-button"
            disabled={videoParseBusy || active}
            onClick={() => void startBatchVideoParse()}
          >
            <Clapperboard size={14} />
            解析 {pendingVideoCount} 条视频
          </button>
        ) : null}
        <span>
          按综合分排序 · 共 {total} 条
          {total > 0 ? ` · 第 ${listPage}/${listPageCount} 页` : ""}
          {listLoading ? " · 加载中" : ""}
        </span>
      </div>

      {manualOpen ? (
        <div
          className="modal-backdrop"
          onMouseDown={() => {
            if (!manualBusy) setManualOpen(false);
          }}
        >
          <div
            className="modal-card hot-research-manual-modal"
            onMouseDown={(event) => event.stopPropagation()}
          >
            <header className="modal-header">
              <div>
                <span className="eyebrow">资料池</span>
                <h3>手动添加素材</h3>
              </div>
              <button
                className="icon-button"
                type="button"
                disabled={manualBusy}
                onClick={() => setManualOpen(false)}
                aria-label="关闭"
              >
                <X size={16} />
              </button>
            </header>
            <div className="hot-research-manual-body">
              <p>
                粘贴文章或视频页面链接，系统会访问该地址采集标题、摘要，并自动标注站点（如
                B站、YouTube）与内容形态。
              </p>
              <label className="hot-research-manual-field">
                <span>
                  <Link2 size={14} />
                  素材链接
                </span>
                <input
                  type="text"
                  inputMode="url"
                  spellCheck={false}
                  autoComplete="off"
                  value={manualUrl}
                  onChange={(event) => setManualUrl(event.target.value)}
                  placeholder="https://www.bilibili.com/video/… 或文章 URL"
                  disabled={manualBusy}
                  autoFocus
                  onKeyDown={(event) => {
                    if (event.key === "Enter") {
                      event.preventDefault();
                      void submitManualItem();
                    }
                  }}
                />
              </label>
            </div>
            <div className="modal-actions">
              <button
                className="secondary-button"
                type="button"
                disabled={manualBusy}
                onClick={() => setManualOpen(false)}
              >
                取消
              </button>
              <button
                className="primary-button"
                type="button"
                disabled={manualBusy || !manualUrl.trim()}
                onClick={() => void submitManualItem()}
              >
                {manualBusy ? "采集中…" : "确定"}
              </button>
            </div>
          </div>
        </div>
      ) : null}

      <div className="hot-research-list">
        {itemCount === 0 ? (
          <div className="hot-news-empty">
            <Search size={22} />
            <strong>还没有资料</strong>
            <span>
              点击「补充资料与核验」检索，或「添加素材」粘贴链接；无资料也可拟定骨架并生成文章。
            </span>
          </div>
        ) : total === 0 ? (
          <div className="hot-news-empty">
            <Search size={22} />
            <strong>没有符合条件的资料</strong>
            <span>可以调整搜索词或类型筛选后再试。</span>
          </div>
        ) : (
          <>
          {items.map((item) => {
            const isVideo = String(item.mediaLabel || "") === "视频";
            const durationLabel = isVideo
              ? formatVideoDuration(
                  typeof item.videoDurationSeconds === "number"
                    ? item.videoDurationSeconds
                    : null,
                )
              : null;
            const parseStatus = String(item.videoParseStatus || "none");
            const parseStep = String(item.videoParseStep || "");
            const parseError = String(item.videoParseError || "");
            const itemId = String(item.id);
            const itemParsing =
              videoParseBusy &&
              videoParseScope === "item" &&
              videoParseItemId === itemId;
            const showItemProgress =
              itemParsing || (isVideo && parseStatus === "running" && videoParseScope === "item");
            const itemStep = itemParsing
              ? videoParseStep || parseStep
              : parseStep;
            const itemProgress = itemParsing ? videoParseProgress : 0;
            const scoreDetails = researchItemScoreDetails(item);
            const scoreDims = researchItemDimensions(scoreDetails);
            const recommendLabel =
              RESEARCH_RECOMMEND_LABELS[String(scoreDetails?.recommendUse || "")] ||
              "";
            return (
              <article
                className={`hot-research-item${itemParsing ? " is-parsing" : ""}`}
                key={itemId}
              >
                <div className="hot-research-item-head">
                  <div>
                    <span className="hot-research-labels">
                      <span className="hot-research-site">
                        {String(item.siteLabel || "未知来源")}
                      </span>
                      <span className="hot-research-label">
                        {item.isAiGenerated
                          ? "AI 分析"
                          : String(item.sourceLabel || "公开资料")}
                      </span>
                      <span className="hot-research-media">
                        {String(item.mediaLabel || "文章")}
                      </span>
                      {durationLabel ? (
                        <span className="hot-research-media hot-research-duration">
                          {durationLabel}
                        </span>
                      ) : null}
                      {isVideo ? (
                        <span
                          className={`hot-research-parse-status status-${
                            itemParsing ? "running" : parseStatus
                          }`}
                        >
                          {itemParsing
                            ? "解析中"
                            : PARSE_STATUS_LABELS[parseStatus] || parseStatus}
                          {(itemParsing || parseStatus === "running") && itemStep
                            ? ` · ${VIDEO_STEP_LABELS[itemStep] || itemStep}`
                            : ""}
                        </span>
                      ) : null}
                      {isVideo && String(item.videoContentKind || "") === "visual" ? (
                        <span className="hot-research-media">画面解析</span>
                      ) : null}
                    </span>
                    <h3>{String(item.title)}</h3>
                  </div>
                  <strong>
                    {String(item.overallScore || 0)}
                    <small>综合分</small>
                  </strong>
                </div>
                <p>{String(item.contentSummary || "暂无摘要")}</p>
                {scoreDims.length > 0 || scoreDetails?.riskNote || recommendLabel ? (
                  <details className="hot-research-scoring">
                    <summary>
                      <span>评分依据{recommendLabel ? ` · ${recommendLabel}` : ""}</span>
                      <span className="hot-research-scoring-toggle">
                        <span className="when-closed">展开详情</span>
                        <span className="when-open">收起详情</span>
                        <ChevronDown size={15} />
                      </span>
                    </summary>
                    {scoreDims.length > 0 ? (
                      <div className="hot-research-score-grid">
                        {scoreDims.map((dim) => (
                          <span key={dim.key || dim.label}>
                            <strong>{dim.score == null ? "-" : dim.score}</strong>
                            {dim.label || dim.key || "维度"}
                            {dim.weight != null ? (
                              <em className="hot-dim-weight">{dim.weight}%</em>
                            ) : null}
                          </span>
                        ))}
                      </div>
                    ) : null}
                    {scoreDims.some((dim) => (dim.reason || "").trim()) ? (
                      <div className="hot-research-dim-reasons">
                        {scoreDims
                          .filter((dim) => (dim.reason || "").trim())
                          .map((dim) => (
                            <small key={`reason-${dim.key || dim.label}`}>
                              {dim.label || dim.key}：{dim.reason}
                            </small>
                          ))}
                      </div>
                    ) : null}
                    {scoreDetails?.riskNote ? (
                      <small className="hot-research-score-risk">
                        说明：{scoreDetails.riskNote}
                      </small>
                    ) : null}
                  </details>
                ) : null}
                {showItemProgress ? (
                  <div className="hot-research-item-progress">
                    <div
                      className="hot-research-progress"
                      role="progressbar"
                      aria-valuenow={Math.max(4, itemProgress)}
                      aria-valuemin={0}
                      aria-valuemax={100}
                    >
                      <i style={{ width: `${Math.max(4, itemProgress || 8)}%` }} />
                    </div>
                    <small>
                      {formatParseProgressLine(
                        itemStep,
                        itemParsing ? videoParseMessage : "",
                        itemParsing ? itemProgress : null,
                      )}
                    </small>
                  </div>
                ) : null}
                {isVideo && parseStatus === "failed" && parseError && !itemParsing ? (
                  <em className="hot-research-parse-error">
                    <AlertTriangle size={13} />
                    失败原因：{friendlyParseError(parseError, parseStep)}
                  </em>
                ) : null}
                <div className="hot-research-meta">
                  <span>
                    站点：{String(item.siteLabel || "未知来源")}
                    {" · "}
                    {String(item.sourceLabel || "公开资料")}
                  </span>
                  <span>
                    采集于{" "}
                    {item.fetchedAt
                      ? formatLocaleDateTime(String(item.fetchedAt))
                      : "未知时间"}
                  </span>
                </div>
                <div className="hot-research-item-actions">
                  {Boolean(item.url) && (
                    <a href={String(item.url)} target="_blank" rel="noreferrer">
                      <ExternalLink size={14} />
                      打开原文
                    </a>
                  )}
                  {isVideo ? (
                    <button
                      className="secondary-button"
                      disabled={videoParseBusy || active}
                      onClick={() =>
                        void startItemVideoParse(
                          String(item.id),
                          parseStatus === "succeeded"
                            ? String(item.videoContentKind || "") === "visual"
                              ? "download_video"
                              : "fetch_subtitle"
                            : undefined,
                        )
                      }
                    >
                      <Clapperboard size={14} />
                      {parseStatus === "succeeded"
                        ? "重新解析"
                        : parseStatus === "running"
                          ? "重新排队"
                          : "解析"}
                    </button>
                  ) : null}
                  {isVideo && parseStatus === "failed" && parseStep ? (
                    <button
                      className="secondary-button"
                      disabled={videoParseBusy || active}
                      title="从失败步骤继续，尽量复用已下载文件"
                      onClick={() =>
                        void startItemVideoParse(String(item.id), parseStep)
                      }
                    >
                      从失败步骤重试
                    </button>
                  ) : null}
                  {isVideo && parseStatus === "succeeded" ? (
                    <button
                      className="secondary-button"
                      onClick={() => setResultItem(item)}
                    >
                      <Eye size={14} />
                      查看解析结果
                    </button>
                  ) : null}
                </div>
              </article>
            );
          })}
          {total > 0 ? (
            <nav className="source-management-pager hot-research-pager" aria-label="资料分页">
              <span className="source-management-pager-total">共 {total} 条</span>
              <div className="source-management-pager-controls">
                <button
                  type="button"
                  className="source-management-pager-nav"
                  disabled={listPage <= 1 || listLoading}
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
                      className={`source-management-pager-page${
                        token === listPage ? " active" : ""
                      }`}
                      disabled={listLoading}
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
                  disabled={listPage >= listPageCount || listLoading}
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
                  className={`source-management-pager-size-trigger${
                    pageSizeOpen ? " open" : ""
                  }`}
                  disabled={listLoading}
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
          </>
        )}
      </div>

      {conflicts.length > 0 && (
        <section className="hot-research-conflicts">
          <h2>冲突证据</h2>
          {conflicts.map((conflict) => (
            <article key={String(conflict.id)}>
              <strong>{String(conflict.claim || "冲突提示")}</strong>
              <p>
                {String(conflict.detail || conflict.reason || "不同来源存在说法差异。")}
              </p>
            </article>
          ))}
        </section>
      )}

      {resultItem ? (
        <VideoParseResultModal
          item={resultItem}
          onClose={() => setResultItem(null)}
        />
      ) : null}
    </>
  );
}
