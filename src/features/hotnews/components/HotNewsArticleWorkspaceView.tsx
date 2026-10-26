// @ts-nocheck
import {
  AlertTriangle,
  Archive,
  ArrowLeft,
  BookOpen,
  CheckCircle2,
  Circle,
  ClipboardCheck,
  Copy,
  Eye,
  FileText,
  FolderSearch,
  History,
  Image,
  MonitorCheck,
  RefreshCcw,
  Save,
  ShieldCheck,
  Sparkles,
  X,
} from "lucide-react";
import { useEffect, useRef, useState } from "react";
import {
  createHotNewsPublishingSnapshot,
  getHotNewsArticle,
  getHotNewsArticleSkeleton,
  getHotNewsAudit,
  getHotNewsHumanReview,
  getHotNewsTopic,
  listHotNewsArticleImages,
  listHotNewsArticleGenerationRuns,
  listHotNewsArticleVersions,
  listHotNewsBriefs,
  listHotNewsPublishingSnapshots,
  resolveHotNewsDocumentIdFromLocation,
  runHotNewsAudit,
  submitHotArticleReviewDecision,
  saveHotNewsArticleContent,
  startHotNewsArticleGeneration,
  updateHotNewsGenerationSettings,
  withDocumentIdQuery,
} from "../api";
import {
  assertWorkflowFeatureAvailable,
  WorkflowFeatureKey,
} from "../../workflow/api";
import type {
  HotNewsArticle,
  HotNewsArticleSkeleton,
  HotNewsArticleVersion,
  HotNewsAudit,
  HotNewsBrief,
  HotNewsGeneratedImage,
  HotNewsPublishingSnapshot,
  HotNewsTopic,
  HotNewsWorkflowNode,
} from "../api";
import {
  HOT_NEWS_DOCUMENT_KIND_DRAFT,
  HOT_NEWS_DOCUMENT_KIND_WECHAT,
} from "../model";
import { PublishingWorkspace } from "../../publishing/components/PublishingWorkspace";
import { loadJobEventHistory } from "../../../shared/api/jobs";
import { formatLocaleDateTime } from "../../../shared/format/time";
import { usePolling } from "../../../shared/hooks/usePolling";
import {
  HOT_NEWS_AUDIT_CATALOG,
  WorkspaceBriefBlock,
  SkeletonModal,
  ContentPanel,
  ReviewPanel,
  VersionModal,
  SnapshotModal,
  normalizeWordOption,
  normalizeImageOption,
} from "./articleWorkspacePanels";

const tabs = [
  ["draft", "热点原稿", FileText],
  ["article", "公众号文章", BookOpen],
  ["images", "配套图片", Image],
  ["mdnice", "公众号排版", MonitorCheck],
  ["review", "审核", ClipboardCheck],
  ["versions", "版本", Archive],
] as const;
/** 由页面拆出的视图。 */
export function HotNewsArticleWorkspaceView(props: Record<string, any>) {
  const {
    article,
    articleId,
    audit,
    auditing,
    body,
    brief,
    changeTab,
    copyMarkdown,
    copyState,
    dirty,
    draft,
    freezePublishingSnapshot,
    generate,
    imageOption,
    images,
    lifecycle,
    mode,
    navigate,
    nodes,
    progress,
    review,
    reviewComment,
    reviewStatus,
    runAudit,
    running,
    save,
    saving,
    selectedSnapshot,
    selectedVersion,
    setDirty,
    setDraft,
    setImageOption,
    setMode,
    setPublishingDirty,
    setReviewComment,
    setSelectedSnapshot,
    setSelectedVersion,
    setSkeletonOpen,
    setTab,
    setVersionTab,
    setWechat,
    setWordOption,
    showNotice,
    skeleton,
    skeletonOpen,
    snapshots,
    status,
    statusToneClass,
    tab,
    taskMessage,
    topic,
    versionTab,
    versions,
    wechat,
    wordOption,
  } = props;
  return (
    <main className="page-shell hot-article-workspace">
      <section className="hot-detail-card">
        <div className="detail-hero hot-detail-hero">
          <button
            className="back-to-route hot-back-link"
            onClick={() =>
              navigate(`/modes/hot-news/topics/${article.topicId}/research`)
            }
          >
            <ArrowLeft size={14} />
            返回选题资料池
          </button>
          <div className="hot-detail-copy">
            <div className="hot-detail-series-row">
              <div className="course-series">热点文章工作区</div>
              <span className={`status-pill detail-status ${statusToneClass}`}>
                {status}
              </span>
            </div>
            <h2 className="hot-detail-title">{article.title}</h2>
            <div className="hot-detail-meta-row">
              <div className="lifecycle-controls">
                <span className={draft ? "on" : ""}>原稿</span>
                <span className={wechat ? "on" : ""}>公众号</span>
                <span className={audit ? "on" : ""}>审核</span>
                <span className={brief?.status === "confirmed" ? "on" : ""}>
                  简报
                </span>
              </div>
              <div className="hot-topic-metrics">
                <span>热度 {topic?.heatScore ?? "-"}</span>
                <span>写作 {topic?.writingScore ?? "-"}</span>
                <span>
                  {topic?.sourceCount ?? article.sourceSnapshot.length} 个来源
                </span>
                <span>{lifecycle}</span>
              </div>
            </div>
          </div>
          <div className="hot-detail-aside">
            <div className="detail-actions hot-primary-actions">
              <button
                className="primary-button unified-generation-button"
                disabled={running || dirty}
                onClick={() => void generate("full")}
              >
                <Sparkles size={15} />
                {running ? `${progress}%` : "生成热点文章"}
              </button>
              <button
                className="ghost-button trajectory-button"
                onClick={() =>
                  navigate(
                    withDocumentIdQuery(
                      `/modes/hot-news/articles/${article.id}/execution`,
                      article.documentId,
                    ),
                  )
                }
              >
                <History size={15} />
                查看执行记录
              </button>
              <button
                className="ghost-button"
                onClick={() =>
                  navigate(`/modes/hot-news/topics/${article.topicId}/research`)
                }
              >
                <FolderSearch size={15} />
                热点资料
              </button>
            </div>
            <div
              className="generation-options hot-aligned-generation-options"
              aria-label="热点文章生成配置"
            >
              <label>
                <span>正文字数</span>
                <select
                  value={wordOption}
                  disabled={running}
                  onChange={(event) => setWordOption(event.target.value)}
                >
                  <option value="1500-2500">1500-2500 字</option>
                  <option value="2500-4000">2500-4000 字（推荐）</option>
                  <option value="4000-6000">4000-6000 字</option>
                </select>
              </label>
              <label>
                <span>正文插图</span>
                <select
                  value={imageOption}
                  disabled={running}
                  onChange={(event) => setImageOption(event.target.value)}
                >
                  <option value="2-3">2-3 张</option>
                  <option value="3-5">3-5 张（推荐）</option>
                  <option value="6-8">6-8 张</option>
                </select>
              </label>
              <p className="hot-generation-hint">
                封面必出；正文插图至少 2 张。字数/插图档与骨架配图要求在点击生成时生效。
              </p>
            </div>
            {taskMessage ? (
              <div className={`detail-task-status ${running ? "running" : ""}`}>
                {running && <RefreshCcw size={14} />}
                <span>
                  {taskMessage}
                  {running ? ` · ${progress}%` : ""}
                </span>
                <button
                  onClick={() =>
                    navigate(
                      withDocumentIdQuery(
                        `/modes/hot-news/articles/${article.id}/execution`,
                        article.documentId,
                      ),
                    )
                  }
                >
                  查看轨迹
                </button>
              </div>
            ) : null}
          </div>
          <WorkspaceBriefBlock
            brief={brief}
            canOpenSkeleton={Boolean(skeleton?.coreThesis)}
            onOpenSkeleton={() => setSkeletonOpen(true)}
          />
        </div>
        <nav className="tab-strip hot-content-tabs" aria-label="热点文章内容">
          {tabs.map(([key, label, Icon]) => (
            <button
              className={tab === key ? "tab active" : "tab"}
              key={key}
              onClick={() => void changeTab(key)}
            >
              <Icon size={15} />
              {label}
            </button>
          ))}
        </nav>
        {(tab === "draft" || tab === "article") && (
          <ContentPanel
            kind={tab}
            body={body}
            words={tab === "draft" ? article.draftWordCount : article.wordCount}
            mode={mode}
            dirty={dirty}
            saving={saving}
            copyState={copyState}
            onMode={setMode}
            onCopy={() => void copyMarkdown()}
            onChange={(value) => {
              tab === "draft" ? setDraft(value) : setWechat(value);
              setDirty(true);
            }}
            onSave={() => void save()}
            onCancel={() => {
              setDraft(article.draftContent || "");
              setWechat(article.content);
              setDirty(false);
              setMode("preview");
            }}
          />
        )}
        {tab === "images" && (
          <div className="image-grid hot-aligned-image-grid">
            {images.length ? (
              images.map((item) => (
                <div className="image-tile" key={item.id}>
                  <img
                    className="image-preview"
                    src={item.publicUrl}
                    alt={
                      item.kind === "cover"
                        ? "热点文章封面"
                        : `正文插图 ${item.sequenceNo || ""}`
                    }
                  />
                  <strong>
                    {item.kind === "cover"
                      ? "封面图"
                      : `正文插图 ${item.sequenceNo}`}
                  </strong>
                  <small>
                    AI 生成图 · {item.width} x {item.height} · {item.prompt}
                  </small>
                </div>
              ))
            ) : (
              <div className="empty-version">尚未生成配套图片。</div>
            )}
          </div>
        )}
        {tab === "review" && (
          <ReviewPanel
            audit={audit}
            auditing={auditing}
            dirty={dirty}
            hasArticle={Boolean(wechat)}
            reviewStatus={reviewStatus}
            comment={reviewComment}
            onComment={setReviewComment}
            onAudit={() => void runAudit()}
            onReview={(value) => void review(value)}
          />
        )}
        {tab === "versions" && (
          <div className="version-list">
            {versions.length ? (
              versions.map((item) => (
                <div className="version-row" key={item.id}>
                  <span>
                    <strong>
                      v{item.versionNo} · {item.reason}
                    </strong>
                    <em className="version-kinds">
                      {item.draftContent ? "原稿 + " : ""}公众号
                    </em>
                    <small>
                      {new Date(item.createdAt).toLocaleString("zh-CN")}
                    </small>
                  </span>
                  <div className="version-actions">
                    <button
                      onClick={() => {
                        setSelectedVersion(item);
                        setVersionTab(item.draftContent ? "draft" : "article");
                      }}
                    >
                      查看版本内容
                    </button>
                    <button
                      disabled={!item.workflowRunId}
                      onClick={() =>
                        navigate(
                          withDocumentIdQuery(
                            `/modes/hot-news/articles/${article.id}/execution`,
                            article.documentId,
                          ),
                        )
                      }
                    >
                      查看生成轨迹
                    </button>
                    <button
                      onClick={() => {
                        setDraft(item.draftContent || "");
                        setWechat(item.content);
                        setTab(item.draftContent ? "draft" : "article");
                        setMode("edit");
                        setDirty(true);
                      }}
                    >
                      恢复此版本
                    </button>
                  </div>
                </div>
              ))
            ) : (
              <div className="empty-version">尚无历史版本。</div>
            )}
          </div>
        )}
        {tab === "mdnice" && (
          wechat.trim() ? (
            <PublishingWorkspace
              articleId={article.id}
              documentId={article.documentId}
              articleVersionNo={
                versions[0]?.versionNo ?? article.currentVersionNo ?? 1
              }
              initialMarkdown={wechat}
              onDirtyChange={setPublishingDirty}
              showNotice={showNotice}
              onCreateSnapshot={freezePublishingSnapshot}
              historySlot={
                <section className="hot-snapshot-history">
                  <header>
                    <h2>排版快照</h2>
                    <span>{snapshots.length} 份</span>
                  </header>
                  {snapshots.length === 0 ? (
                    <p className="muted-text">尚未生成发布快照。</p>
                  ) : (
                    snapshots.map((item) => (
                      <button
                        className="snapshot-row"
                        key={item.id}
                        onClick={() => setSelectedSnapshot(item)}
                      >
                        <strong>
                          {new Date(item.createdAt).toLocaleString("zh-CN")}
                        </strong>
                        <Eye size={16} />
                      </button>
                    ))
                  )}
                </section>
              }
            />
          ) : (
            <div className="publishing-empty">
              当前还没有公众号文章，生成后再进行原生排版。
            </div>
          )
        )}
      </section>
      {selectedVersion && (
        <VersionModal
          version={selectedVersion}
          tab={versionTab}
          onTab={setVersionTab}
          onClose={() => setSelectedVersion(null)}
        />
      )}
      {selectedSnapshot && (
        <SnapshotModal
          article={article}
          snapshot={selectedSnapshot}
          onClose={() => setSelectedSnapshot(null)}
        />
      )}
      {skeletonOpen && skeleton?.coreThesis ? (
        <SkeletonModal
          skeleton={skeleton}
          onClose={() => setSkeletonOpen(false)}
        />
      ) : null}
      <span className="hot-workflow-summary" hidden>
        {nodes.length}
      </span>
    </main>
  );
}
