import { TabContentPreviews } from "./TabContentPreviews";
import { TabVersionsPanel } from "./TabVersionsPanel";
import { TabReviewPanel } from "./TabReviewPanel";
import { useEffect, useMemo, useRef, useState } from "react";
import {
  AlertTriangle,
  BookOpen,
  Bot,
  CheckCircle2,
  ChevronDown,
  ChevronRight,
  Clipboard,
  ClipboardCheck,
  ExternalLink,
  History,
  Image,
  MonitorCheck,
  Palette,
  Play,
  RefreshCcw,
  Search,
  Sparkles,
  Trash2,
  Undo2,
  Wand2,
  WandSparkles,
  X,
} from "lucide-react";
import type {
  ArticleData,
  ContentThemeData,
  CourseGenerationSettingsData,
  ImageCountOption,
  WordCountOption,
} from "../api";
import {
  deleteContentTheme,
  patchContentTheme,
  repairArticle,
  repairArticleMetadata,
  resumeArticleRepair,
  runArticleAudit,
  themePathSlug,
} from "../api";
import { formatExecutionTime } from "../../executions/pages/ExecutionTrajectoryPage";
import { PublishingWorkspace } from "../../publishing/components/PublishingWorkspace";
import {
  type ArticleVersion,
  type AuditItem,
  type AuditReport,
  type Course,
  type CourseContent,
  type HumanReview,
  type HumanReviewStatus,
  type SyncedImage,
  type TabKey,
  type UnifiedContentVersion,
  emptyAuditReport,
  formatCourseStatus,
  imageCountLabels,
  imageNameFromSrc,
  localizedAuditItem,
  nativePublishingEnabled,
  normalizeObsidianEmbeds,
  preferCourseDocumentId,
  prepareMdniceMarkdown,
  resolveImageSrc,
  statusTone,
  tabs,
  wordCountLabels,
} from "../model";
import { EditableMarkdown, MarkdownReader } from "./courseEditorUi";

export function TabContent({
  course,
  content,
  syncedImages,
  tab,
  articleVersions,
  draftArticleVersions,
  onChangeContent,
  onResetContent,
  onRestoreVersion,
  onViewTrajectory,
  onRepairApplied,
  humanReview,
  onSubmitHumanReview,
  onResetHumanReview,
  onDeleteImage,
  onConfirm,
  nativePublishingEnabled,
  onPublishingDirtyChange,
  showNotice,
}: {
  course: Course;
  content: CourseContent;
  syncedImages: SyncedImage[];
  tab: TabKey;
  articleVersions: ArticleVersion[];
  draftArticleVersions: ArticleVersion[];
  onChangeContent: (
    kind: "draft" | "article",
    value: string,
    source?: string,
    reason?: string,
  ) => Promise<void>;
  onResetContent: (kind: "draft" | "article") => Promise<void>;
  onRestoreVersion: (versionId: string) => Promise<void>;
  onViewTrajectory: (workflowRunId: string) => void;
  onRepairApplied: (article: ArticleData) => Promise<void>;
  humanReview?: HumanReview;
  onSubmitHumanReview: (
    status: Exclude<HumanReviewStatus, "pending">,
    comment: string,
  ) => void;
  onResetHumanReview: () => void;
  onDeleteImage: (src: string) => Promise<void>;
  onConfirm: (
    title: string,
    message: string,
    tone?: "default" | "danger",
  ) => Promise<boolean>;
  nativePublishingEnabled: boolean;
  onPublishingDirtyChange: (dirty: boolean) => void;
  showNotice: (title: string, message: string) => void;
}) {
  const [copyState, setCopyState] = useState<"idle" | "copied" | "failed">(
    "idle",
  );
  const [reviewComment, setReviewComment] = useState(
    humanReview?.comment ?? "",
  );
  const [autoFixBackup, setAutoFixBackup] = useState<string | null>(null);
  const [fixApplied, setFixApplied] = useState(false);
  const [aiFixing, setAiFixing] = useState(false);
  const [aiFixProgress, setAiFixProgress] = useState("");
  const [aiFixError, setAiFixError] = useState("");
  const [previewVersion, setPreviewVersion] = useState<ArticleVersion | null>(
    null,
  );
  const [previewVersionTab, setPreviewVersionTab] = useState<
    "draft" | "article"
  >("draft");
  const [auditReport, setAuditReport] = useState<AuditReport>(emptyAuditReport);
  const [auditLoading, setAuditLoading] = useState(false);
  const [auditRefreshKey, setAuditRefreshKey] = useState(0);
  const skipNextAudit = useRef(false);
  const [rechecking, setRechecking] = useState(false);
  const resumedRepair = useRef<string | null>(null);
  const [fixAuditSummary, setFixAuditSummary] = useState<{
    before: AuditReport;
    after: AuditReport;
  } | null>(null);

  useEffect(() => {
    setReviewComment(humanReview?.comment ?? "");
  }, [course.id, humanReview?.comment]);

  useEffect(() => {
    setAutoFixBackup(null);
    setFixApplied(false);
    setRechecking(false);
    setFixAuditSummary(null);
    setAuditReport(emptyAuditReport);
  }, [course.id]);
  const mdniceMarkdown = useMemo(
    () => prepareMdniceMarkdown(content.article || course.articleExcerpt || ""),
    [content.article, course.articleExcerpt],
  );

  useEffect(() => {
    let cancelled = false;
    if (tab !== "review") return;
    if (skipNextAudit.current) {
      skipNextAudit.current = false;
      return;
    }
    if (!content.articleArticleId) {
      setAuditReport(emptyAuditReport);
      return;
    }
    const articleDocumentId = preferCourseDocumentId(
      content.articleDocumentId,
      content.articleArticleId,
    );
    setAuditLoading(true);
    runArticleAudit(content.articleArticleId, articleDocumentId)
      .then((report) => {
        if (!cancelled) {
          setAuditReport(report);
          setAiFixError("");
        }
      })
      .catch((error) => {
        if (!cancelled) {
          setAiFixError(
            error instanceof Error ? error.message : "自动审核失败。",
          );
        }
      })
      .finally(() => {
        if (!cancelled) setAuditLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [
    auditRefreshKey,
    content.article,
    content.articleArticleId,
    content.articleDocumentId,
    tab,
  ]);

  /** Copy the normalized mdnice Markdown and expose transient status feedback. */
  async function copyMdniceMarkdown() {
    // Clipboard status is transient and does not alter article content.
    try {
      await navigator.clipboard.writeText(mdniceMarkdown);
      setCopyState("copied");
      window.setTimeout(() => setCopyState("idle"), 1800);
    } catch {
      setCopyState("failed");
    }
  }

  /** Open mdnice in an isolated browser tab. */
  function openMdniceEditor() {
    // Open mdnice in an isolated tab without granting opener access.
    window.open("https://editor.mdnice.com/", "_blank", "noopener,noreferrer");
  }

  /** Whether an audit item can be fixed by metadata repair (image assets). */
  function isImageAssetRepairItem(item: AuditItem) {
    return (
      item.status !== "pass" &&
      (item.code === "IMAGE_METADATA_COMPLETE" ||
        item.code === "IMAGE_ASSETS_PRESENT")
    );
  }

  /** Run the backend repair workflow and apply only its committed response. */
  async function runAiFix() {
    // The repair API owns prompt construction, AI invocation, versioning, and automatic re-audit.
    const article = content.article || course.articleExcerpt || "";
    if (!article.trim()) {
      setAiFixError("当前没有公众号文章，无法修复。");
      return;
    }
    setAiFixing(true);
    setAiFixError("");
    setFixApplied(false);
    setFixAuditSummary(null);
    setAiFixProgress("正在准备自动修复");
    try {
      if (!content.articleArticleId) throw new Error("当前公众号文章不存在。");
      const articleDocumentId = preferCourseDocumentId(
        content.articleDocumentId,
        content.articleArticleId,
      );
      const beforeAudit = auditReport;
      let currentAudit = auditReport;
      if (currentAudit.items.some(isImageAssetRepairItem)) {
        setAiFixProgress("正在补齐图片资产信息");
        currentAudit = await repairArticleMetadata(
          content.articleArticleId,
          articleDocumentId,
        );
        setAuditReport(currentAudit);
      }
      if (
        !currentAudit.items.some(
          (item) => item.status !== "pass" && item.fixableByTextAi,
        )
      ) {
        setFixAuditSummary({ before: beforeAudit, after: currentAudit });
        setFixApplied(true);
        if (
          currentAudit.warnings === beforeAudit.warnings &&
          currentAudit.failed === beforeAudit.failed &&
          currentAudit.score === beforeAudit.score &&
          currentAudit.items.some(isImageAssetRepairItem)
        ) {
          setAiFixError(
            "图片资产警告仍在：审核需要能解析到图片名称与路径。若正文里已有图，请重新打开审核页刷新；若仍失败，到「配套图片」确认图片可正常预览。",
          );
        }
        return;
      }
      setAiFixProgress("AI 修复任务已排队");
      const result = await repairArticle(
        content.articleArticleId,
        (event) => setAiFixProgress(`${event.progress}%`),
        articleDocumentId,
      );
      await applyRepairResult(result, article);
    } catch (error) {
      setRechecking(false);
      setAiFixError(error instanceof Error ? error.message : "AI 修复失败。");
    } finally {
      setAiFixing(false);
      setAiFixProgress("");
    }
  }

  /** Synchronize one completed background repair into the unchanged review UI. */
  async function applyRepairResult(
    result: Awaited<ReturnType<typeof repairArticle>>,
    backup: string,
  ) {
    setAutoFixBackup(backup);
    setRechecking(true);
    setFixApplied(false);
    // Avoid creating a duplicate audit when the committed article is copied into local state.
    skipNextAudit.current = true;
    await onRepairApplied(result.article);
    setAuditReport(result.afterAudit);
    setFixAuditSummary({
      before: result.beforeAudit,
      after: result.afterAudit,
    });
    setRechecking(false);
    setFixApplied(true);
  }

  useEffect(() => {
    const articleId = content.articleArticleId;
    if (tab !== "review" || !articleId || resumedRepair.current === articleId)
      return;
    resumedRepair.current = articleId;
    if (!window.localStorage.getItem(`workbench-active-repair:${articleId}`))
      return;
    const backup = content.article || course.articleExcerpt || "";
    resumeArticleRepair(
      articleId,
      (event) => setAiFixProgress(`${event.progress}%`),
      () => {
        setAiFixing(true);
        setAiFixProgress("0%");
      },
    )
      .then(async (result) => {
        if (!result) return;
        await applyRepairResult(result, backup);
      })
      .catch((error) =>
        setAiFixError(error instanceof Error ? error.message : "AI 修复失败。"),
      )
      .finally(() => {
        setAiFixing(false);
        setAiFixProgress("");
      });
  }, [content.article, content.articleArticleId, course.articleExcerpt, tab]);

  /** Restore the in-memory pre-repair body as a new versioned edit. */
  async function undoAutoFix() {
    // Undo is a normal versioned restore; it never deletes the persisted repair history.
    if (autoFixBackup === null) return;
    await onChangeContent(
      "article",
      autoFixBackup,
      "restore",
      "撤销 AI 自动修复",
    );
    setAuditRefreshKey((current) => current + 1);
    setAutoFixBackup(null);
    setFixApplied(false);
    setFixAuditSummary(null);
    setRechecking(false);
  }

  // Hooks must run in the same order for every tab. Build the shared version
  // view model before any tab-specific early return.
  const unifiedVersions = useMemo<UnifiedContentVersion[]>(() => {
    const grouped = new Map<number, UnifiedContentVersion>();
    for (const version of [...articleVersions, ...draftArticleVersions]) {
      const current = grouped.get(version.versionNo);
      if (!current) {
        grouped.set(version.versionNo, {
          ...version,
          ...(articleVersions.includes(version)
            ? { articleVersion: version }
            : { draftVersion: version }),
        });
      } else if (articleVersions.includes(version)) {
        grouped.set(version.versionNo, {
          ...version,
          draftVersion: current.draftVersion,
          articleVersion: version,
        });
      } else {
        current.draftVersion = version;
      }
    }
    return [...grouped.values()].sort((a, b) => b.versionNo - a.versionNo);
  }, [articleVersions, draftArticleVersions]);

  if (tab === "draft" || tab === "article" || tab === "images") {
    return (
      <TabContentPreviews
        tab={tab}
        course={course}
        content={content}
        syncedImages={syncedImages}
        onChangeContent={onChangeContent}
        onResetContent={onResetContent}
        onConfirm={onConfirm}
        onDeleteImage={onDeleteImage}
      />
    );
  }

  if (tab === "mdnice") {
    if (nativePublishingEnabled)
      return (
        <PublishingWorkspace
          articleId={content.articleArticleId}
          documentId={preferCourseDocumentId(
            content.articleDocumentId,
            content.articleArticleId,
          )}
          articleVersionNo={articleVersions[0]?.versionNo ?? 1}
          initialMarkdown={content.article || course.articleExcerpt || ""}
          onDirtyChange={onPublishingDirtyChange}
          showNotice={showNotice}
        />
      );
  }

  if (!nativePublishingEnabled && tab === "mdnice") {
    const lineCount = mdniceMarkdown ? mdniceMarkdown.split("\n").length : 0;
    const imageCount = [...mdniceMarkdown.matchAll(/!\[[^\]]*]\([^)]+\)/g)]
      .length;

    return (
      <div className="mdnice-frame">
        <div className="browser-bar">
          <span />
          <span />
          <span />
          <strong>editor.mdnice.com</strong>
        </div>
        <div className="mdnice-toolbar">
          <div>
            <strong>mdnice 发布稿</strong>
            <small>
              {mdniceMarkdown.length} 字符 · {lineCount} 行 · {imageCount}{" "}
              张图片
            </small>
          </div>
          <div className="mdnice-actions">
            <button
              className="ghost-button"
              disabled={!mdniceMarkdown}
              onClick={copyMdniceMarkdown}
            >
              <Clipboard size={15} />
              {copyState === "copied"
                ? "已复制"
                : copyState === "failed"
                  ? "复制失败"
                  : "复制 Markdown"}
            </button>
            <button className="ghost-button" onClick={openMdniceEditor}>
              <ExternalLink size={15} />
              打开 mdnice
            </button>
          </div>
        </div>
        <div className="mdnice-body">
          <textarea
            className="mdnice-editor"
            readOnly
            spellCheck={false}
            value={mdniceMarkdown || "当前课程还没有公众号文章。"}
          />
          <div className="mdnice-preview">
            {mdniceMarkdown ? (
              <MarkdownReader
                content={mdniceMarkdown}
                hideImageCaptions
                hideImageHeadings
              />
            ) : (
              <p>当前课程还没有可排版内容。</p>
            )}
          </div>
        </div>
      </div>
    );
  }

  if (tab === "review") {
    return (
      <TabReviewPanel
        course={course}
        content={content}
        humanReview={humanReview}
        onSubmitHumanReview={onSubmitHumanReview}
        onResetHumanReview={onResetHumanReview}
        onConfirm={onConfirm}
        reviewComment={reviewComment}
        setReviewComment={setReviewComment}
        auditReport={auditReport}
        auditLoading={auditLoading}
        aiFixing={aiFixing}
        aiFixProgress={aiFixProgress}
        aiFixError={aiFixError}
        setAiFixError={setAiFixError}
        rechecking={rechecking}
        fixApplied={fixApplied}
        fixAuditSummary={fixAuditSummary}
        runAiFix={runAiFix}
        undoAutoFix={undoAutoFix}
        isImageAssetRepairItem={isImageAssetRepairItem}
      />
    );
  }

  if (tab === "versions") {
    return (
      <TabVersionsPanel
        draftArticleVersions={draftArticleVersions}
        previewVersion={previewVersion}
        setPreviewVersion={setPreviewVersion}
        previewVersionTab={previewVersionTab}
        setPreviewVersionTab={setPreviewVersionTab}
        unifiedVersions={unifiedVersions}
        formatExecutionTime={formatExecutionTime}
        onRestoreVersion={onRestoreVersion}
        onViewTrajectory={onViewTrajectory}
        onConfirm={onConfirm}
      />
    );
  }

  return null;
}
