import { useEffect, useMemo, useRef, useState } from "react";
import {
  loadAiSettingsFromDatabase,
  saveAiSettingsToDatabase,
} from "../features/settings/api";
import { loadWechatMpSettings } from "../features/settings/api";
import {
  loadArticleVersions,
  loadHumanReview,
  loadCourseContent,
  loadChengwenfangData,
  syncLearningContent,
  migrateLegacyBrowserContent,
  migrateLegacyHumanReviews,
  requestImageGeneration,
  deleteGeneratedImage,
  resetHumanReview as resetHumanReviewApi,
  restoreArticleVersion,
  saveHumanReview,
  saveArticleContent,
  listCourseGenerationJobs,
  loadCourseGenerationSettings,
  saveCourseGenerationSettings,
  listContentThemes,
  restoreContentTheme,
  hotNewsThemePath,
  parseHotNewsThemeSearchQueries,
  parseHotNewsThemeGithubQuery,
} from "../features/course/api";
import type {
  ArticleData,
  ArticleVersionData,
  ContentThemeData,
  CourseGenerationSettingsData,
} from "../features/course/api";
import { requestContentGeneration } from "../features/course/api";
import { requestCourseContentGeneration } from "../features/course/api";
import { setCoursePublished } from "../features/course/api";
import { unwrapApiResult } from "../shared/api/http";
import {
  loadJob,
  loadJobArticleVersions,
  loadJobEventHistory,
  loadJobNodes,
  retryFailedJob,
  type JobData,
  type JobEventData,
  type WorkflowNodeData,
} from "../shared/api/jobs";
import { formatHomeJobTime } from "../shared/format/time";
import { usePolling } from "../shared/hooks/usePolling";
import {
  buildHotNewsWorkspacePath,
  matchLegacyHotNewsWorkspacePath,
} from "./legacyRedirects";
import { parseRoute } from "./routes";
import { useRouter } from "./useRouter";
import {
  findThemeBySlug,
  themeCodeFromSlug,
  themePathSlug,
} from "../shared/theme/themeSlug";
import {
  assertWorkflowFeatureAvailable,
  listWorkflowRuns,
  listWorkflows,
  WorkflowFeatureKey,
} from "../features/workflow/api";
import type { WorkflowSummary } from "../features/workflow/api";
import {
  listHotNewsArticles,
  listHotNewsConfirmedTopics,
  listHotNewsRadarCollectRuns,
  listHotNewsRuns,
  getHotNewsArticle,
  getHotNewsTopic,
  resolveHotNewsDocumentIdFromLocation,
  withDocumentIdQuery,
} from "../features/hotnews/api";
import { defaultAiSettings } from "../features/settings/model";
import type { AiSettings } from "../features/settings/model";
import { listSkills } from "../features/settings/api";
import type { Skill } from "../features/settings/api";
import { WorkflowPage } from "../features/workflow/pages/WorkflowPage";
import { NotificationCenter } from "../features/notifications/pages/NotificationCenter";
import {
  ExecutionTrajectoryPage,
  jobStatusLabel,
} from "../features/executions/pages/ExecutionTrajectoryPage";
import { AiSettingsPage } from "../features/settings/pages/SettingsPage";
import { CourseSystemShell } from "../features/course/pages/CourseSystemShell";
import { HotNewsModeShell } from "../features/hotnews/pages/HotNewsModeShell";
import {
  Activity,
  AlertTriangle,
  CheckCircle2,
  ChevronDown,
  ChevronRight,
  FileText,
  GitBranch,
  Info,
  Layers3,
  LayoutDashboard,
  LibraryBig,
  Search,
  Settings,
  Trash2,
  X,
} from "lucide-react";
import {
  type ArticleVersion,
  type Course,
  type CourseContent,
  type HumanReview,
  type HumanReviewStatus,
  type SyncedImage,
  type TabKey,
  type ChengwenfangData,
  imageCountLabels,
  imageNameFromSrc,
  preferCourseDocumentId,
  extractIllustrationPrompts,
  extractMarkdownImages,
  uniqueImages,
  wordCountLabels,
} from "../features/course/model";
import { text } from "../features/hotnews/components/hotNewsExecutionHelpers";

export type HomeRecentWorkflowTask = {
  jobId: string;
  workflowKey: string;
  workflowScene: string;
  workflowName: string;
  runStatus: string;
  at: string;
  job: JobData | null;
};

export type AppDialogState = {
  title: string;
  message: string;
  mode: "notice" | "confirm" | "prompt";
  tone?: "default" | "danger";
  inputLabel?: string;
  placeholder?: string;
  minLength?: number;
};

export function AppDialog({
  dialog,
  onClose,
}: {
  dialog: AppDialogState;
  onClose: (result?: boolean | string | null) => void;
}) {
  const [inputValue, setInputValue] = useState("");
  useEffect(() => {
    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === "Escape")
        onClose(dialog.mode === "prompt" ? null : false);
    };
    window.addEventListener("keydown", handleKeyDown);
    return () => window.removeEventListener("keydown", handleKeyDown);
  }, [dialog.mode, onClose]);

  const isDanger = dialog.tone === "danger";
  const promptTooShort =
    dialog.mode === "prompt" &&
    Boolean(dialog.minLength) &&
    inputValue.trim().length < dialog.minLength!;
  return (
    <div
      className="modal-backdrop app-dialog-backdrop"
      onClick={() => onClose(dialog.mode === "prompt" ? null : false)}
    >
      <section
        className={`app-dialog ${isDanger ? "danger" : ""}`}
        role="alertdialog"
        aria-modal="true"
        aria-labelledby="app-dialog-title"
        aria-describedby="app-dialog-message"
        onClick={(event) => event.stopPropagation()}
      >
        <div className="app-dialog-icon" aria-hidden="true">
          {isDanger ? <AlertTriangle size={21} /> : <Info size={21} />}
        </div>
        <div className="app-dialog-copy">
          <h3 id="app-dialog-title">{dialog.title}</h3>
          <p id="app-dialog-message">{dialog.message}</p>
          {dialog.mode === "prompt" && (
            <label className="app-dialog-input">
              <span>{dialog.inputLabel || "输入内容"}</span>
              <textarea
                value={inputValue}
                minLength={dialog.minLength}
                placeholder={dialog.placeholder}
                autoFocus
                onChange={(event) => setInputValue(event.target.value)}
              />
              {dialog.minLength && (
                <small>
                  {inputValue.trim().length} / {dialog.minLength} 字
                </small>
              )}
            </label>
          )}
        </div>
        <button
          className="app-dialog-close"
          aria-label="关闭"
          onClick={() => onClose(dialog.mode === "prompt" ? null : false)}
        >
          <X size={17} />
        </button>
        <footer className="app-dialog-actions">
          {dialog.mode !== "notice" && (
            <button
              onClick={() => onClose(dialog.mode === "prompt" ? null : false)}
            >
              取消
            </button>
          )}
          <button
            className={isDanger ? "danger-action" : "primary-action"}
            autoFocus={dialog.mode !== "prompt"}
            disabled={promptTooShort}
            onClick={() =>
              onClose(dialog.mode === "prompt" ? inputValue.trim() : true)
            }
          >
            {dialog.mode === "prompt"
              ? "提交"
              : dialog.mode === "confirm"
                ? "确认"
                : "知道了"}
          </button>
        </footer>
      </section>
    </div>
  );
}

export function pickHomeFeaturedWorkflows(workflows: WorkflowSummary[]) {
  const markers = [
    WorkflowFeatureKey.HOT_NEWS_RADAR_COLLECT,
    WorkflowFeatureKey.COURSE_CONTENT_GENERATION,
    WorkflowFeatureKey.HOT_NEWS_ARTICLE_GENERATION,
  ];
  const active = workflows.filter((item) => item.status !== "archived");
  const picked: WorkflowSummary[] = [];
  for (const marker of markers) {
    const matched = active.find((item) => {
      const text = `${item.scene || ""} ${item.key || ""}`.toLowerCase();
      return text.includes(marker);
    });
    if (matched) picked.push(matched);
  }
  return picked;
}

/** 首页最近任务的类型展示名。 */

export function homeJobTypeLabel(type: string) {
  if (type.includes("radar") || type.includes("fetch")) return "热点采集";
  if (type.includes("hot_news_article") || type.includes("hot_article"))
    return "热点成文";
  if (type.includes("hot_news") || type.startsWith("hot_")) return "热点任务";
  if (type.includes("wechat")) return "公众号文章";
  if (type.includes("course_draft")) return "课程原稿";
  if (type.includes("course_content")) return "课程内容";
  if (type.includes("repair")) return "文章修复";
  if (type.includes("image")) return "图片生成";
  if (type.includes("review")) return "文章审核";
  if (type.includes("draft")) return "原稿生成";
  return "内容任务";
}

/** 最近任务左侧标签：优先工作流场景，其次 job.type。 */

export function homeWorkflowTypeLabel(
  key: string,
  scene: string,
  workflowName: string,
  jobType?: string,
) {
  const marker = `${scene} ${key}`.toLowerCase();
  if (marker.includes(WorkflowFeatureKey.HOT_NEWS_RADAR_COLLECT))
    return "热点采集";
  if (marker.includes(WorkflowFeatureKey.HOT_NEWS_ARTICLE_GENERATION))
    return "热点成文";
  if (marker.includes(WorkflowFeatureKey.COURSE_CONTENT_GENERATION))
    return "课程内容";
  if (jobType) return homeJobTypeLabel(jobType);
  return workflowName || "工作流";
}

/** 最近任务主文案。 */

export function homeRecentTaskSubject(
  task: HomeRecentWorkflowTask,
  courseList: Array<Pick<Course, "id" | "title">>,
  titleByEntityId?: Record<string, string>,
) {
  if (task.job) {
    return homeJobSubject(task.job, courseList, titleByEntityId);
  }
  return task.workflowName || task.jobId;
}

/** 是否雷达/采集类任务（不进入「最近生成成功」文章列表）。 */

export function isRadarOrCollectJob(job: JobData) {
  const type = job.type || "";
  return (
    type.includes("radar") ||
    type.includes("fetch") ||
    type.includes("collect")
  );
}

/** 首页「最近生成成功」：仅课程/热点成文类成功任务。 */

export function isHomeArticleSuccessJob(job: JobData) {
  if (job.status !== "succeeded") return false;
  if (isRadarOrCollectJob(job)) return false;
  const type = job.type || "";
  return (
    type.includes("course_content") ||
    type.includes("hot_news_article") ||
    type.includes("hot_article") ||
    type.includes("wechat")
  );
}

/** 进度文案是否无业务含义（如完成态的 done），不应当标题。 */

export function isGenericJobProgressLabel(value: string) {
  const normalized = value.trim().toLowerCase();
  return (
    !normalized ||
    normalized === "done" ||
    normalized === "succeeded" ||
    normalized === "completed" ||
    normalized === "success" ||
    normalized === "failed" ||
    normalized === "running" ||
    normalized === "pending" ||
    normalized === "cancelled"
  );
}

/** 首页任务关联对象名：课名缓存 → 当前主题课程 → meta → 非泛化进度文案。 */

export function homeJobSubject(
  job: JobData,
  courseList: Array<Pick<Course, "id" | "title">>,
  titleByEntityId?: Record<string, string>,
) {
  const cached = titleByEntityId?.[job.entityId]?.trim();
  if (cached) return cached;
  const course = courseList.find((item) => item.id === job.entityId);
  if (course?.title) return course.title;
  const metaTitle = job.progressMeta?.title;
  if (typeof metaTitle === "string" && metaTitle.trim()) {
    return metaTitle.trim();
  }
  const message = (job.progressMessage || "").trim();
  if (message && !isGenericJobProgressLabel(message)) return message;
  const step = (job.currentStep || "").trim();
  if (step && !isGenericJobProgressLabel(step)) return step;
  return job.entityId || job.id;
}

/** 「最近生成成功」主标题：课程名 / 文章标题。 */

export function homeArticleDisplayTitle(
  job: JobData,
  courseList: Array<Pick<Course, "id" | "title">>,
  titleByEntityId?: Record<string, string>,
) {
  return homeJobSubject(job, courseList, titleByEntityId);
}

/** 首页任务时间的短格式。 */

export function homeWorkflowRunLabel(status: string) {
  if (status === "succeeded" || status === "completed") return "最近成功";
  if (status === "failed") return "最近失败";
  if (status === "running") return "执行中";
  if (status === "pending" || status === "queued") return "排队中";
  if (status === "cancelled") return "已取消";
  return status || "尚无执行";
}

/** Sidebar navigation command with consistent icon and active styling. */

export function NavButton({
  active,
  icon: Icon,
  label,
  onClick,
}: {
  active: boolean;
  icon: typeof LayoutDashboard;
  label: string;
  onClick: () => void;
}) {
  return (
    <button
      className={active ? "nav-button active" : "nav-button"}
      onClick={onClick}
    >
      <Icon size={18} />
      <span>{label}</span>
    </button>
  );
}
