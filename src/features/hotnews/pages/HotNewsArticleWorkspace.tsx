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
  listHotNewsWorkflowNodes,
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
} from "../components/articleWorkspacePanels";
import { HotNewsArticleWorkspaceView } from "../components/HotNewsArticleWorkspaceView";

type TabKey = "draft" | "article" | "images" | "mdnice" | "review" | "versions";
type Props = {
  articleId: string;
  navigate: (path: string) => void;
  showNotice: (title: string, message: string) => void;
  confirmAction: (
    title: string,
    message: string,
    tone?: "default" | "danger",
  ) => Promise<boolean>;
  promptAction: (
    title: string,
    message: string,
    options?: {
      inputLabel?: string;
      placeholder?: string;
      minLength?: number;
      tone?: "default" | "danger";
    },
  ) => Promise<string | null>;
};
const tabs = [
  ["draft", "热点原稿", FileText],
  ["article", "公众号文章", BookOpen],
  ["images", "配套图片", Image],
  ["mdnice", "公众号排版", MonitorCheck],
  ["review", "审核", ClipboardCheck],
  ["versions", "版本", Archive],
] as const;

/** 进入审核页即可看到的检查项，与引擎目录对齐。 */
export function HotNewsArticleWorkspace({
  articleId,
  navigate,
  showNotice,
  confirmAction,
  promptAction,
}: Props) {
  const [article, setArticle] = useState<HotNewsArticle | null>(null);
  const [topic, setTopic] = useState<HotNewsTopic | null>(null);
  const [brief, setBrief] = useState<HotNewsBrief | null>(null);
  const [skeleton, setSkeleton] = useState<HotNewsArticleSkeleton | null>(null);
  const [skeletonOpen, setSkeletonOpen] = useState(false);
  const [draft, setDraft] = useState("");
  const [wechat, setWechat] = useState("");
  const [tab, setTab] = useState<TabKey>("draft");
  const [mode, setMode] = useState<"preview" | "edit">("preview");
  const [dirty, setDirty] = useState(false);
  const [saving, setSaving] = useState(false);
  const [copyState, setCopyState] = useState("idle");
  const [wordOption, setWordOption] = useState("2500-4000");
  const [imageOption, setImageOption] = useState("3-5");
  const [running, setRunning] = useState(false);
  const [progress, setProgress] = useState(0);
  const [taskMessage, setTaskMessage] = useState("");
  const [nodes, setNodes] = useState<HotNewsWorkflowNode[]>([]);
  const [images, setImages] = useState<HotNewsGeneratedImage[]>([]);
  const [versions, setVersions] = useState<HotNewsArticleVersion[]>([]);
  const [selectedVersion, setSelectedVersion] =
    useState<HotNewsArticleVersion | null>(null);
  const [versionTab, setVersionTab] = useState<"draft" | "article">("draft");
  const [audit, setAudit] = useState<HotNewsAudit | null>(null);
  const [auditing, setAuditing] = useState(false);
  const [reviewStatus, setReviewStatus] = useState<string | null>(null);
  const [reviewComment, setReviewComment] = useState("");
  const [publishingDirty, setPublishingDirty] = useState(false);
  const [snapshots, setSnapshots] = useState<HotNewsPublishingSnapshot[]>([]);
  const [selectedSnapshot, setSelectedSnapshot] =
    useState<HotNewsPublishingSnapshot | null>(null);
  const activeJobIdRef = useRef<string | null>(null);
  /** 跨刷新保留 documentId（含深链 ?documentId= / ?job=） */
  const documentIdRef = useRef<string | null>(null);

  async function reload() {
    // 深链首次从 query/job 解析；之后用 ref / 已加载 article
    const seedDocumentId = await resolveHotNewsDocumentIdFromLocation(
      documentIdRef.current || article?.documentId,
    );
    const loaded = await getHotNewsArticle(articleId, seedDocumentId);
    if (loaded.documentId) {
      documentIdRef.current = loaded.documentId;
    }
    const [
      loadedTopic,
      briefs,
      loadedSkeleton,
      history,
      imageHistory,
      report,
      review,
      snapshotHistory,
    ] = await Promise.all([
      getHotNewsTopic(loaded.topicId),
      listHotNewsBriefs(loaded.topicId),
      getHotNewsArticleSkeleton(loaded.topicId).catch(() => null),
      listHotNewsArticleVersions(articleId, loaded.documentId),
      listHotNewsArticleImages(articleId, loaded.documentId),
      getHotNewsAudit(articleId, loaded.documentId).catch(() => null),
      getHotNewsHumanReview(articleId, loaded.documentId).catch(() => null),
      listHotNewsPublishingSnapshots(articleId, loaded.documentId).catch(() => []),
    ]);
    setArticle(loaded);
    setTopic(loadedTopic);
    setBrief(briefs.find((item) => item.id === loaded.briefId) || null);
    setSkeleton(loadedSkeleton);
    setDraft(loaded.draftContent || "");
    setWechat(loaded.content);
    setWordOption(
      normalizeWordOption(loaded.generationSettings?.wordCountOption),
    );
    setImageOption(
      normalizeImageOption(loaded.generationSettings?.imageCountOption),
    );
    setVersions(history);
    setImages(imageHistory);
    setSnapshots(snapshotHistory);
    setAudit(report?.contentHash === loaded.contentHash ? report : null);
    setReviewStatus(
      review?.contentHash === loaded.contentHash ? review.status : null,
    );
    if (loaded.workflowRunId)
      setNodes(
        await listHotNewsWorkflowNodes(loaded.workflowRunId).catch(() => []),
      );
  }
  useEffect(() => {
    void reload().catch((error) =>
      showNotice(
        "文章加载失败",
        error instanceof Error ? error.message : "请稍后重试",
      ),
    );
  }, [articleId]);
  const autoAuditKeyRef = useRef<string | null>(null);
  useEffect(() => {
    if (tab !== "review" || !article || !wechat.trim() || dirty || auditing) return;
    const items = audit?.items || [];
    const stale = items.some((item) =>
      [
        "SOURCE_APPENDIX_REQUIRED",
        "SOURCE_APPENDIX_AT_END",
        "SOURCE_REFERENCE_COMPLETE",
        "ARTICLE_TITLE_REQUIRED",
      ].includes(item.code),
    );
    if (items.length > 0 && !stale) return;
    const key = `${article.id}:${article.contentHash || ""}`;
    // 失败不占坑；旧规则结果强制重跑
    if (!stale && autoAuditKeyRef.current === key) return;
    // 失败也占坑，避免审核页静默死循环打接口；用户可点「重新审核」
    autoAuditKeyRef.current = key;
    void runAudit({ silent: true }).then((ok) => {
      if (!ok) {
        /* 保留 key，防止 auditing 结束后 effect 再次自动重试 */
      }
    });
  }, [tab, article, wechat, dirty, audit, auditing]);
  useEffect(() => {
    let active = true;
    async function resumeGeneration() {
      const jobs = await listHotNewsArticleGenerationRuns(articleId);
      const job = jobs.find((item) =>
        ["pending", "running"].includes(item.status),
      );
      if (!active) return;
      if (!job) {
        if (activeJobIdRef.current !== null) {
          activeJobIdRef.current = null;
          setRunning(false);
          setTaskMessage("");
        }
        return;
      }
      activeJobIdRef.current = job.id;
      setRunning(true);
      setProgress(job.progress);
      setTaskMessage(
        job.status === "pending"
          ? "热点文章生产任务正在排队"
          : "热点文章仍在后台执行，页面会自动更新",
      );
      try {
        const events = await loadJobEventHistory(job.id);
        if (!active || activeJobIdRef.current !== job.id) return;
        const latest = events.at(-1);
        if (latest) {
          setProgress(latest.progress || job.progress);
          setTaskMessage(latest.message || "正在执行热点文章工作流");
        }
      } catch {
        /* keep the last visible status when the event feed is unavailable */
      } finally {
        if (active && activeJobIdRef.current === job.id) setRunning(true);
      }
    }
    void resumeGeneration().catch((error) => {
      if (!active) return;
      setTaskMessage(
        error instanceof Error ? error.message : "任务状态读取失败",
      );
    });
    const timer = window.setInterval(() => {
      void resumeGeneration().catch(() => {
        /* keep polling */
      });
    }, 3000);
    return () => {
      active = false;
      window.clearInterval(timer);
    };
  }, [articleId]);
  useEffect(() => {
    const warn = (event: BeforeUnloadEvent) => {
      if (dirty) {
        event.preventDefault();
        event.returnValue = "";
      }
    };
    window.addEventListener("beforeunload", warn);
    return () => window.removeEventListener("beforeunload", warn);
  }, [dirty]);
  if (!article)
    return (
      <main className="page-shell">
        <div className="empty-state">正在加载文章工作区...</div>
      </main>
    );

  const body = tab === "draft" ? draft : wechat;
  const status =
    wechat && !draft
      ? "历史文章 · 待补原稿"
      : article.status === "ready"
        ? "待生产"
        : article.status === "generating"
          ? "生成中"
          : article.status === "completed"
            ? "已完成"
            : article.status || "待生产";
  const statusToneClass =
    status === "待生产" || status.includes("待补")
      ? "tone-amber"
      : status === "生成中"
        ? "tone-blue"
        : status === "已完成"
          ? "tone-green"
          : "tone-muted";
  const lifecycle =
    (
      {
        rising: "热度上升期",
        peak: "热度高峰期",
        stable: "稳定期",
        fading: "热度回落期",
      } as Record<string, string>
    )[topic?.lifecycle || ""] || "生命周期未知";
  async function changeTab(next: TabKey) {
    if (next === tab) return;
    if (tab === "mdnice" && publishingDirty) {
      const leave = await confirmAction(
        "离开公众号排版？",
        "文章内容有未保存修改。离开后这些修改会丢失。",
        "danger",
      );
      if (!leave) return;
    }
    if (dirty) {
      const leave = await confirmAction(
        "放弃未保存修改？",
        "当前内容尚未保存，切换栏目后这些修改会丢失。",
        "danger",
      );
      if (!leave) return;
    }
    setDraft(article!.draftContent || "");
    setWechat(article!.content);
    setDirty(false);
    setPublishingDirty(false);
    setMode("preview");
    setTab(next);
  }
  async function save() {
    if (tab !== "draft" && tab !== "article") return;
    setSaving(true);
    try {
      const next = await saveHotNewsArticleContent(
        article!.id,
        tab === "draft"
          ? HOT_NEWS_DOCUMENT_KIND_DRAFT
          : HOT_NEWS_DOCUMENT_KIND_WECHAT,
        {
          content: tab === "draft" ? draft : wechat,
          baseContentHash:
            tab === "draft" ? article!.draftContentHash : article!.contentHash,
          // draft 轨用 draftDocumentId；公众号轨用 documentId
          documentId:
            tab === "draft" ? article!.draftDocumentId : article!.documentId,
        },
      );
      setArticle(next);
      setDraft(next.draftContent || "");
      setWechat(next.content);
      setDirty(false);
      setMode("preview");
      setVersions(await listHotNewsArticleVersions(next.id, next.documentId));
      setAudit(null);
      setReviewStatus(null);
      showNotice("内容已保存", `已保存为 V${next.currentVersionNo}`);
    } catch (error) {
      showNotice(
        "保存失败",
        error instanceof Error ? error.message : "请刷新后重试",
      );
    } finally {
      setSaving(false);
    }
  }
  async function generate(
    scope: "full" | "draft" | "wechat" | "images" | "audit",
  ) {
    const scopeLabel =
      scope === "full"
        ? "完整热点原稿、公众号成品和配套图片"
        : scope === "draft"
          ? "热点原稿"
          : scope === "wechat"
            ? "公众号成品文章"
            : scope === "images"
              ? "配套图片"
              : "文章审核结果";
    const confirmed = await confirmAction(
      "开始生成任务？",
      `即将生成${scopeLabel}。\n\n目标字数：${wordOption}\n正文插图：${imageOption}（不含封面）\n\n任务会在后台执行，失败后可以从失败步骤重试。`,
    );
    if (!confirmed)
      return;
    setRunning(true);
    setProgress(0);
    setTaskMessage("任务正在排队");
    try {
      // 点击时反查「生成热点公众号文章」工作流；停用则提示并中止
      await assertWorkflowFeatureAvailable(
        WorkflowFeatureKey.HOT_NEWS_ARTICLE_GENERATION,
      );
      await updateHotNewsGenerationSettings(article!.id, {
        wordCountOption: wordOption,
        imageCountOption: imageOption,
        documentId: article!.documentId,
      });
      const job = await startHotNewsArticleGeneration(article!.id, {
        scope,
        wordCountOption: wordOption,
        imageCountOption: imageOption,
        documentId: article!.documentId,
      });
      // 入队后跳转执行轨迹，进度在轨迹页查看
      navigate(
        withDocumentIdQuery(
          `/modes/hot-news/articles/${encodeURIComponent(article!.id)}/execution?job=${encodeURIComponent(job.id)}`,
          article!.documentId || job.documentId,
        ),
      );
    } catch (error) {
      const message = error instanceof Error ? error.message : "请查看执行记录";
      setTaskMessage(message);
      showNotice("生成失败", message);
      setRunning(false);
    }
  }
  async function copyMarkdown() {
    try {
      await navigator.clipboard.writeText(body);
      setCopyState("copied");
      window.setTimeout(() => setCopyState("idle"), 1500);
    } catch {
      setCopyState("failed");
    }
  }
  async function runAudit(options?: { silent?: boolean }): Promise<boolean> {
    if (!article) return false;
    setAuditing(true);
    try {
      const result = await runHotNewsAudit(article.id, article.documentId);
      setAudit(result);
      if (!options?.silent) {
        showNotice(
          (result.blockingCount || result.failed || 0) > 0 ? "审核发现阻断问题" : "自动审核通过",
          result.summary || "审核结果已更新。",
        );
      }
      return true;
    } catch (error) {
      showNotice(
        "自动审核失败",
        error instanceof Error ? error.message : "请稍后重试",
      );
      return false;
    } finally {
      setAuditing(false);
    }
  }
  async function review(value: "approved" | "rejected") {
    try {
      if (!audit) throw new Error("请先完成独立审核");
      const result = await submitHotArticleReviewDecision(audit.id, value, reviewComment);
      setReviewStatus(String(result.status));
      showNotice(
        value === "approved" ? "人工审核已通过" : "文章已退回",
        value === "approved" ? "当前版本可以进入排版阶段。" : reviewComment,
      );
    } catch (error) {
      showNotice(
        "审核提交失败",
        error instanceof Error ? error.message : "请稍后重试",
      );
    }
  }
  async function freezePublishingSnapshot(themeVersionId: string) {
    if (!article) throw new Error("文章不存在");
    const item = await createHotNewsPublishingSnapshot(
      article.id,
      themeVersionId,
      article.documentId,
    );
    setSnapshots((current) => [item, ...current]);
    showNotice("发布快照已生成", "当前文章与主题已经冻结。");
  }
  return (
    <HotNewsArticleWorkspaceView
      article={article}
      articleId={articleId}
      audit={audit}
      auditing={auditing}
      body={body}
      brief={brief}
      changeTab={changeTab}
      copyMarkdown={copyMarkdown}
      copyState={copyState}
      dirty={dirty}
      draft={draft}
      freezePublishingSnapshot={freezePublishingSnapshot}
      generate={generate}
      imageOption={imageOption}
      images={images}
      lifecycle={lifecycle}
      mode={mode}
      navigate={navigate}
      nodes={nodes}
      progress={progress}
      review={review}
      reviewComment={reviewComment}
      reviewStatus={reviewStatus}
      runAudit={runAudit}
      running={running}
      save={save}
      saving={saving}
      selectedSnapshot={selectedSnapshot}
      selectedVersion={selectedVersion}
      setDirty={setDirty}
      setDraft={setDraft}
      setImageOption={setImageOption}
      setMode={setMode}
      setPublishingDirty={setPublishingDirty}
      setReviewComment={setReviewComment}
      setSelectedSnapshot={setSelectedSnapshot}
      setSelectedVersion={setSelectedVersion}
      setSkeletonOpen={setSkeletonOpen}
      setTab={setTab}
      setVersionTab={setVersionTab}
      setWechat={setWechat}
      setWordOption={setWordOption}
      showNotice={showNotice}
      skeleton={skeleton}
      skeletonOpen={skeletonOpen}
      snapshots={snapshots}
      status={status}
      statusToneClass={statusToneClass}
      tab={tab}
      taskMessage={taskMessage}
      topic={topic}
      versionTab={versionTab}
      versions={versions}
      wechat={wechat}
      wordOption={wordOption}
    />
  );
}
