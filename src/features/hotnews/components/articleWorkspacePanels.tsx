import {
  Copy,
  Image,
  Save,
  ShieldCheck,
  X,
} from "lucide-react";
import ReactMarkdown from "react-markdown";
import remarkGfm from "remark-gfm";
import {
  ReviewPanel as SharedReviewPanel,
  type ReviewAuditItemView,
} from "../../../shared/components/ReviewPanel";
import { SkeletonModal } from "../../../shared/components/SkeletonModal";
import type {
  HotNewsArticle,
  HotNewsArticleVersion,
  HotNewsAudit,
  HotNewsBrief,
  HotNewsPublishingSnapshot,
} from "../api";
import { withPublishingPreviewBase } from "../../publishing/model";

export { SkeletonModal };

export const HOT_NEWS_AUDIT_CATALOG = [
  { code: "ARTICLE_TITLE", title: "文章主标题", category: "文章结构", detail: "须有且仅有一个有效一级标题。" },
  { code: "ARTICLE_SECTIONS", title: "小标题有效", category: "文章结构", detail: "二级标题不能为空、不得重复，长度 1 到 32 个字符。" },
  { code: "ARTICLE_STRUCTURE", title: "段落结构完整", category: "文章结构", detail: "文章应解析出可审核的段落。" },
  { code: "ARTICLE_MIN_LENGTH", title: "文章长度", category: "文章结构", detail: "正文长度需达到发布下限。" },
  { code: "ARTICLE_MARKDOWN_VALID", title: "Markdown 结构有效", category: "文章结构", detail: "代码块围栏应成对闭合。" },
  { code: "NO_EMPTY_BLOCK", title: "段落内容完整", category: "文章结构", detail: "二级标题下不能是空段落。" },
  { code: "SOURCE_PRESENT", title: "选题来源完整", category: "事实与来源", detail: "研究快照中须绑定来源。" },
  { code: "SOURCE_METADATA", title: "来源信息有效", category: "事实与来源", detail: "来源名称、标题与可访问链接完整。" },
  { code: "SOURCE_DUPLICATE", title: "来源不重复", category: "事实与来源", detail: "来源 URL 不得重复。" },
  { code: "NO_INTERNAL_LEAK", title: "内部信息不泄漏", category: "公开内容", detail: "正文不得出现内部术语、生成元信息或图片提示词。" },
  { code: "IMAGE_REFERENCE", title: "图片引用有效", category: "资源完整性", detail: "图片 URL 不重复引用。" },
  { code: "LINK_REFERENCE_VALID", title: "正文链接有效", category: "资源完整性", detail: "Markdown 链接格式完整。" },
  { code: "REVIEW_SNAPSHOT_COMPLETE", title: "审核快照完整", category: "版本完整性", detail: "审核使用当前文章内容快照。" },
] as const;


export function WorkspaceBriefBlock({
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
  return (
    <section className="hot-research-brief hot-workspace-brief">
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
      {overview ? <p className="hot-workspace-brief-overview">{overview}</p> : null}
      {coreThesis ? (
        <p>
          <b>核心论点：</b>
          {coreThesis}
        </p>
      ) : null}
      {!overview && !coreThesis ? (
        <p className="hot-research-brief-empty">
          简报内容待补充，可先到选题资料池完善。
        </p>
      ) : null}
    </section>
  );
}

export function asTextList(value: unknown): string[] {
  if (!Array.isArray(value)) return [];
  return value.map((item) => String(item ?? "").trim()).filter(Boolean);
}

export function ContentPanel({
  kind,
  body,
  words,
  mode,
  dirty,
  saving,
  copyState,
  onMode,
  onCopy,
  onChange,
  onSave,
  onCancel,
}: {
  kind: "draft" | "article";
  body: string;
  words: number;
  mode: "preview" | "edit";
  dirty: boolean;
  saving: boolean;
  copyState: string;
  onMode: (value: "preview" | "edit") => void;
  onCopy: () => void;
  onChange: (value: string) => void;
  onSave: () => void;
  onCancel: () => void;
}) {
  return (
    <div
      className={`preview-card ${kind === "article" ? "article-preview" : ""}`}
    >
      <div className="preview-meta">
        <span>{kind === "draft" ? "热点原稿" : "公众号版本"}</span>
        <span>{words} 字</span>
      </div>
      <h3>
        {body
          ? kind === "draft"
            ? "已生成热点原稿"
            : "已生成公众号版本"
          : kind === "draft"
            ? "历史文章无原稿记录"
            : "公众号版本待生成"}
      </h3>
      {!body && kind === "draft" ? (
        <div className="hot-aligned-empty">
          <p>
            这篇文章创建于原稿与公众号文章分离之前，现有公众号正文没有丢失。
          </p>
        </div>
      ) : !body ? (
        <p>当前还没有公众号版本，生成热点原稿后可继续转换。</p>
      ) : (
        <>
          <div className="hot-markdown-toolbar editor-toolbar">
            <span>
              {kind === "draft" ? "热点原稿 Markdown" : "公众号文章 Markdown"}
            </span>
            <div className="segmented-control">
              <button className="markdown-copy-button" onClick={onCopy}>
                <Copy size={14} />
                {copyState === "copied"
                  ? "已复制"
                  : copyState === "failed"
                    ? "复制失败"
                    : "复制 Markdown"}
              </button>
              <button
                className={mode === "preview" ? "active" : ""}
                onClick={() => onMode("preview")}
              >
                预览
              </button>
              <button
                className={mode === "edit" ? "active" : ""}
                onClick={() => onMode("edit")}
              >
                编辑
              </button>
            </div>
          </div>
          {mode === "edit" ? (
            <div className="hot-inline-editor">
              <textarea
                aria-label={
                  kind === "draft" ? "热点原稿 Markdown" : "公众号文章 Markdown"
                }
                value={body}
                onChange={(event) => onChange(event.target.value)}
                spellCheck={false}
              />
              <div>
                <button className="ghost-button" onClick={onCancel}>
                  取消
                </button>
                <button
                  className="primary-button"
                  disabled={!dirty || saving}
                  onClick={onSave}
                >
                  <Save size={14} />
                  {saving ? "保存中" : "保存修改"}
                </button>
              </div>
            </div>
          ) : (
            <article className="markdown-reader hot-single-preview">
              <ReactMarkdown
                remarkPlugins={[remarkGfm]}
                components={{
                  img({ src = "", alt = "" }) {
                    return (
                      <span className="markdown-image-wrap">
                        <img
                          src={src}
                          alt={alt}
                          loading="lazy"
                          onError={(event) => {
                            event.currentTarget
                              .closest(".markdown-image-wrap")
                              ?.classList.add("missing-image");
                          }}
                        />
                        {alt && (
                          <span className="markdown-image-caption">{alt}</span>
                        )}
                      </span>
                    );
                  },
                }}
              >
                {body}
              </ReactMarkdown>
            </article>
          )}
        </>
      )}
    </div>
  );
}

/** 热点文章工作区审核面板：差异参数化后委托 shared ReviewPanel。 */
export function ReviewPanel({
  audit,
  auditing,
  dirty,
  hasArticle,
  reviewStatus,
  comment,
  onComment,
  onAudit,
  onReview,
}: {
  audit: HotNewsAudit | null;
  auditing: boolean;
  dirty: boolean;
  hasArticle: boolean;
  reviewStatus: string | null;
  comment: string;
  onComment: (value: string) => void;
  onAudit: () => void;
  onReview: (value: "approved" | "rejected") => void;
}) {
  const resultItems = audit?.items?.length ? audit.items : [];
  const displayItems = resultItems.length
    ? resultItems
    : HOT_NEWS_AUDIT_CATALOG.map((item) => ({
        id: item.code,
        code: item.code,
        category: item.category,
        title: item.title,
        detail: item.detail,
        status: "pending",
        blocking: false,
      }));
  const scored = resultItems.length > 0;
  const failedCount = audit?.failedCount ?? audit?.failed ?? 0;
  const blockingCount = audit?.blockingCount ?? 0;
  const autoStatus = String(audit?.status || audit?.ruleStatus || "").toLowerCase();
  const canApprove =
    Boolean(audit?.id) &&
    !auditing &&
    scored &&
    autoStatus !== "pending" &&
    autoStatus !== "none" &&
    failedCount === 0 &&
    blockingCount === 0;
  const items: ReviewAuditItemView[] = displayItems.map((item) => {
    const status =
      item.status === "passed" || item.status === "pass"
        ? "pass"
        : item.status === "pending"
          ? "pending"
          : item.blocking ||
              item.status === "failed" ||
              item.status === "fail"
            ? "fail"
            : "warn";
    return {
      key: String(item.id || item.code),
      status,
      title: item.title || item.code,
      body: (
        <>
          {item.category ? <small>{item.category}</small> : null}
          <small>{item.detail}</small>
          {"locations" in item &&
            item.locations?.map((location, index) => (
              <small key={index}>
                原文：{String(location.quote || "未提供定位")}
              </small>
            ))}
          {"evidence" in item &&
            item.evidence?.map((evidence, index) => (
              <small key={index}>
                证据：
                {String(
                  evidence.quote ||
                    evidence.sourceTitle ||
                    "未提供来源证据",
                )}
              </small>
            ))}
        </>
      ),
    };
  });
  return (
    <SharedReviewPanel
      workspaceClassName="review-workspace hot-review-workspace"
      score={
        scored
          ? Math.round(
              ((audit?.passedCount ?? audit?.passed ?? 0) /
                Math.max(1, resultItems.length)) *
                100,
            )
          : 0
      }
      auditHeadingActions={
        <button
          className="auto-fix-trigger"
          disabled={auditing || dirty || !hasArticle}
          onClick={onAudit}
        >
          <ShieldCheck size={16} />
          {auditing ? "审核中" : "重新审核"}
        </button>
      }
      statusBadge={
        <span
          className={`review-status review-status-${reviewStatus || "pending"}`}
        >
          {reviewStatus === "approved"
            ? "人工已通过"
            : reviewStatus === "rejected"
              ? "人工已退回"
              : "待人工审核"}
        </span>
      }
      statusInHumanHeading
      auditSummary={
        auditing
          ? `正在检查 ${displayItems.length} 项...`
          : scored
            ? `已完成 ${resultItems.length} 项检查：${audit?.passedCount ?? audit?.passed ?? 0} 通过，${audit?.warningCount ?? audit?.warnings ?? 0} 警告，${failedCount} 失败。`
            : `将检查 ${displayItems.length} 项，与课程审核一样进入本页后自动执行。`
      }
      items={items}
      comment={comment}
      onComment={onComment}
      canApprove={canApprove}
      approveTitle={
        canApprove
          ? "通过人工审核"
          : auditing
            ? "自动审核进行中"
            : !scored
              ? "请先完成自动审核"
              : failedCount > 0 || blockingCount > 0
                ? "仍有失败或阻断项"
                : "自动审核尚未完成"
      }
      onReject={() => onReview("rejected")}
      onApprove={() => onReview("approved")}
    />
  );
}

export function VersionModal({
  version,
  tab,
  onTab,
  onClose,
}: {
  version: HotNewsArticleVersion;
  tab: "draft" | "article";
  onTab: (value: "draft" | "article") => void;
  onClose: () => void;
}) {
  return (
    <div className="modal-backdrop" onClick={onClose}>
      <section
        className="version-preview-modal"
        onClick={(event) => event.stopPropagation()}
      >
        <header>
          <div>
            <small>版本 v{version.versionNo}</small>
            <h3>查看版本内容</h3>
          </div>
          <button onClick={onClose}>
            <X size={18} />
          </button>
        </header>
        <div className="version-preview-tabs">
          <button
            className={tab === "draft" ? "active" : ""}
            disabled={!version.draftContent}
            onClick={() => onTab("draft")}
          >
            热点原稿
          </button>
          <button
            className={tab === "article" ? "active" : ""}
            onClick={() => onTab("article")}
          >
            公众号版本
          </button>
        </div>
        <div className="version-preview-content">
          <ReactMarkdown remarkPlugins={[remarkGfm]}>
            {tab === "draft"
              ? version.draftContent || "该版本没有热点原稿。"
              : version.content}
          </ReactMarkdown>
        </div>
      </section>
    </div>
  );
}

export function SnapshotModal({
  article,
  snapshot,
  onClose,
}: {
  article: HotNewsArticle;
  snapshot: HotNewsPublishingSnapshot;
  onClose: () => void;
}) {
  return (
    <div className="hot-snapshot-overlay" onMouseDown={onClose}>
      <section
        className="hot-snapshot-modal"
        onMouseDown={(event) => event.stopPropagation()}
      >
        <header>
          <div>
            <h2>{article.title}</h2>
            <small>
              {new Date(snapshot.createdAt).toLocaleString("zh-CN")}
            </small>
          </div>
          <button onClick={onClose}>
            <X size={18} />
          </button>
        </header>
        <iframe
          title="发布快照 HTML 预览"
          srcDoc={withPublishingPreviewBase(snapshot.html)}
        />
      </section>
    </div>
  );
}

/** 规范化字数档，兼容空值。 */
export function normalizeWordOption(option?: string | null) {
  if (option === "1500-2500" || option === "4000-6000" || option === "2500-4000") {
    return option;
  }
  return "2500-4000";
}

/** 规范化正文插图档：封面另计，正文至少 2 张；兼容旧 0 / 1-2。 */
export function normalizeImageOption(option?: string | null) {
  if (option === "2-3" || option === "3-5" || option === "6-8") {
    return option;
  }
  if (option === "0" || option === "1-2" || option === "1" || option === "2") {
    return "2-3";
  }
  return "3-5";
}
