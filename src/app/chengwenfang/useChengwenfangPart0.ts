// @ts-nocheck
import { useEffect, useMemo, useRef, useState } from "react";
import {
  loadAiSettingsFromDatabase,
  saveAiSettingsToDatabase,
} from "../../features/settings/api";
import { loadWechatMpSettings } from "../../features/settings/api";
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
} from "../../features/course/api";
import type {
  ArticleData,
  ArticleVersionData,
  ContentThemeData,
  CourseGenerationSettingsData,
} from "../../features/course/api";
import { requestContentGeneration } from "../../features/course/api";
import { requestCourseContentGeneration } from "../../features/course/api";
import { setCoursePublished } from "../../features/course/api";
import { unwrapApiResult } from "../../shared/api/http";
import {
  loadJob,
  loadJobArticleVersions,
  loadJobEventHistory,
  loadJobNodes,
  retryFailedJob,
  type JobData,
  type JobEventData,
  type WorkflowNodeData,
} from "../../shared/api/jobs";
import { formatHomeJobTime } from "../../shared/format/time";
import { usePolling } from "../../shared/hooks/usePolling";
import {
  buildHotNewsWorkspacePath,
  matchLegacyHotNewsWorkspacePath,
} from "../legacyRedirects";
import { parseRoute } from "../routes";
import { useRouter } from "../useRouter";
import {
  findThemeBySlug,
  themeCodeFromSlug,
  themePathSlug,
} from "../../shared/theme/themeSlug";
import {
  assertWorkflowFeatureAvailable,
  listWorkflowRuns,
  listWorkflows,
  WorkflowFeatureKey,
} from "../../features/workflow/api";
import type { WorkflowSummary } from "../../features/workflow/api";
import {
  listHotNewsArticles,
  listHotNewsConfirmedTopics,
  listHotNewsRadarCollectRuns,
  listHotNewsRuns,
  getHotNewsArticle,
  getHotNewsTopic,
  resolveHotNewsDocumentIdFromLocation,
  withDocumentIdQuery,
} from "../../features/hotnews/api";
import { defaultAiSettings } from "../../features/settings/model";
import type { AiSettings } from "../../features/settings/model";
import { listSkills } from "../../features/settings/api";
import type { Skill } from "../../features/settings/api";
import { jobStatusLabel } from "../../features/executions/pages/ExecutionTrajectoryPage";
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
} from "../../features/course/model";
import {
  pickHomeFeaturedWorkflows,
  isHomeArticleSuccessJob,
  type HomeRecentWorkflowTask,
  type AppDialogState,
} from "../appShellUi";

export function useChengwenfangPart0() {
  // Dashboard state: selected course, active navigation, and visible detail tab.
  const [data, setData] = useState<ChengwenfangData>({
    workspace: {
      name: "成文坊",
      moduleName: "自学课程及自媒体内容生成",
      topicName: "0 基础 AI 自学",
      topicSlug: "ai-basics",
      modeId: "course_system",
      modeName: "课程体系教学模式",
      modeCode: "course_system",
      themeId: "theme-ai-self-study-zero",
      themeName: "0 基础 AI 自学",
      themeCode: "ai_self_study_zero",
    },
    generatedAt: "",
    courses: [],
    metrics: {
      courseCount: 0,
      reviewedCount: 0,
      pendingCount: 0,
      imageCount: 0,
    },
  });
  const [selectedId, setSelectedId] = useState("");
  const [activeTab, setActiveTab] = useState<TabKey>("article");
  const [publishingDirty, setPublishingDirty] = useState(false);
  const [activeNav, setActiveNav] = useState("dashboard");
  const [courseModeExpanded, setCourseModeExpanded] = useState(false);
  const [hotNewsModeExpanded, setHotNewsModeExpanded] = useState(false);
  const [contentThemes, setContentThemes] = useState<ContentThemeData[]>([]);
  const [deletedThemes, setDeletedThemes] = useState<ContentThemeData[]>([]);
  const [themeDeletedNotice, setThemeDeletedNotice] = useState("");
  const [hotNewsThemes, setHotNewsThemes] = useState<ContentThemeData[]>([]);
  const [selectedThemeId, setSelectedThemeId] = useState(
    "theme-ai-self-study-zero",
  );
  const [selectedHotNewsThemeId, setSelectedHotNewsThemeId] = useState(
    "theme-ai-frontier",
  );
  const [themeWizardOpen, setThemeWizardOpen] = useState(false);
  const [themeWizardResumeId, setThemeWizardResumeId] = useState<string | null>(
    null,
  );
  const [hotNewsThemeWizardOpen, setHotNewsThemeWizardOpen] = useState(false);
  const [hotNewsThemeWizardResumeId, setHotNewsThemeWizardResumeId] = useState<
    string | null
  >(null);
  const [themePickerOpen, setThemePickerOpen] = useState(false);
  const [executionThemePickerOpen, setExecutionThemePickerOpen] =
    useState(false);
  const [hotNewsThemePickerOpen, setHotNewsThemePickerOpen] = useState(false);
  const [routeCourseId, setRouteCourseId] = useState<string | null>(null);
  const [routeWorkflowId, setRouteWorkflowId] = useState<string | null>(null);
  const [routeHotNewsTopicId, setRouteHotNewsTopicId] = useState<string | null>(
    null,
  );
  const [routeHotNewsArticleId, setRouteHotNewsArticleId] = useState<
    string | null
  >(null);
  const [routeHotNewsExecutionArticleId, setRouteHotNewsExecutionArticleId] =
    useState<string | null>(null);
  const [routeHotNewsCollectThemeId, setRouteHotNewsCollectThemeId] = useState<
    string | null
  >(null);
  const [routeHotNewsResearchTopicId, setRouteHotNewsResearchTopicId] =
    useState<string | null>(null);
  const [routeHotNewsSourceManagement, setRouteHotNewsSourceManagement] =
    useState(false);
  const [routeHotNewsSelected, setRouteHotNewsSelected] = useState(false);
  const [routeHotNewsRadar, setRouteHotNewsRadar] = useState(false);
  const [searchQuery, setSearchQuery] = useState("");
  const [publishedCourses, setPublishedCourses] = useState<
    Record<string, boolean>
  >(() =>
    JSON.parse(
      window.localStorage.getItem("chengwenfang-published-courses") || "{}",
    ),
  );
  const [taskRunning, setTaskRunning] = useState(false);
  const [taskMessage, setTaskMessage] = useState("");
  const [generationSettings, setGenerationSettings] =
    useState<CourseGenerationSettingsData | null>(null);
  const [generationSettingsLoading, setGenerationSettingsLoading] =
    useState(false);
  const [generationSettingsError, setGenerationSettingsError] = useState("");
  const [appDialog, setAppDialog] = useState<AppDialogState | null>(null);
  const dialogResolver = useRef<
    ((result: boolean | string | null) => void) | null
  >(null);
  const [generationJobs, setGenerationJobs] = useState<JobData[]>([]);
  const [selectedGenerationJobId, setSelectedGenerationJobId] = useState<
    string | null
  >(null);
  const [generationEvents, setGenerationEvents] = useState<JobEventData[]>([]);
  const [generationNodes, setGenerationNodes] = useState<WorkflowNodeData[]>(
    [],
  );
  const [generationVersions, setGenerationVersions] = useState<
    ArticleVersionData[]
  >([]);





  /** Protect an explicitly unsaved publishing draft before switching detail tabs. */

  // Local UI preferences retained while switching between courses and tabs.
  const [checkedRules, setCheckedRules] = useState(["summary"]);
  const [collapsedGroups, setCollapsedGroups] = useState<
    Record<string, boolean>
  >({});
  const [loadState, setLoadState] = useState<"loading" | "ready" | "fallback">(
    "loading",
  );
  const [chengwenfangRefreshKey, setChengwenfangRefreshKey] = useState(0);

  // Database-backed article content and immutable version history.
  const [courseContent, setCourseContent] = useState<CourseContent>({
    draft: "",
    article: "",
    draftArticleId: null,
    articleArticleId: null,
    draftDocumentId: null,
    articleDocumentId: null,
    images: [],
    loading: false,
  });
  const [articleVersions, setArticleVersions] = useState<ArticleVersion[]>([]);
  const [draftArticleVersions, setDraftArticleVersions] = useState<
    ArticleVersion[]
  >([]);

  // Version-bound human review decisions and audit results shown in the review tab.
  const [humanReviews, setHumanReviews] = useState<Record<string, HumanReview>>(
    {},
  );

  // Redacted AI provider settings loaded from and saved to the database.
  const [aiSettings, setAiSettings] = useState<AiSettings>(defaultAiSettings);
  const [aiSettingsUpdatedAt, setAiSettingsUpdatedAt] = useState("");
  const [wechatConfigured, setWechatConfigured] = useState(false);
  const [settingsSectionHint, setSettingsSectionHint] = useState<
    "ai" | "skills" | "layoutThemes" | "wechatMp" | "system" | null
  >(null);
  const [homeSkillCount, setHomeSkillCount] = useState(0);
  const [homeSelectedTopics, setHomeSelectedTopics] = useState(0);
  const [homeHotArticleCount, setHomeHotArticleCount] = useState(0);
  const [homeWorkflows, setHomeWorkflows] = useState<
    Array<{
      id: string;
      key: string;
      scene: string;
      name: string;
      status: string;
      lastRunStatus: string | null;
      lastRunAt: string | null;
      lastSuccessAt: string | null;
      lastSuccessJobId: string | null;
    }>
  >([]);
  /** 首页「最近生成成功」：优先取各工作流最近成功 run 对应的 job。 */
  const [homeRecentSuccessJobs, setHomeRecentSuccessJobs] = useState<JobData[]>(
    [],
  );
  /** 首页任务标题缓存：跨主题稳定显示课名，避免回退成 done。 */
  const [homeJobTitleByEntityId, setHomeJobTitleByEntityId] = useState<
    Record<string, string>
  >({});
  /** 三个主工作流合并后的最近执行记录（最多 5 条）。 */
  const [homeRecentTasks, setHomeRecentTasks] = useState<
    HomeRecentWorkflowTask[]
  >([]);


  function refreshCourseSystemThemes() {
    return Promise.all([
      listContentThemes("course_system", false),
      listContentThemes("course_system", true),
      listContentThemes("hot_news", false),
      listContentThemes("hot_news", true),
    ])
      .then(([active, courseDeleted, hotNewsActive, hotNewsDeleted]) => {
        setContentThemes(active);
        setDeletedThemes(
          [...courseDeleted, ...hotNewsDeleted].sort((left, right) => {
            const leftAt = left.deletedAt || "";
            const rightAt = right.deletedAt || "";
            return rightAt.localeCompare(leftAt);
          }),
        );
        setHotNewsThemes(hotNewsActive);
        setSelectedHotNewsThemeId((current) => {
          if (hotNewsActive.some((theme) => theme.id === current)) {
            return current;
          }
          const enabled = hotNewsActive.find((theme) => theme.status === "enabled");
          return enabled?.id || hotNewsActive[0]?.id || "theme-ai-frontier";
        });
      })
      .catch(() => {
        setContentThemes([]);
        setDeletedThemes([]);
        setHotNewsThemes([]);
      });
  }
  useEffect(() => {
    void refreshCourseSystemThemes();
  }, []);

  useEffect(() => {
    let cancelled = false;
    loadWechatMpSettings()
      .then((settings) => {
        if (!cancelled) {
          setWechatConfigured(
            Boolean(settings.appSecretConfigured && settings.appId?.trim()),
          );
        }
      })
      .catch(() => {
        if (!cancelled) setWechatConfigured(false);
      });
    return () => {
      cancelled = true;
    };
  }, []);

  useEffect(() => {
    let cancelled = false;
    loadAiSettingsFromDatabase()
      .then(async (result) => {
        if (cancelled) return;
        if (!result.configured) {
          const legacy = window.localStorage.getItem("chengwenfang-ai-settings");
          if (legacy) {
            try {
              const parsed = JSON.parse(legacy) as Partial<AiSettings>;
              const migrated = await saveAiSettingsToDatabase({
                ...defaultAiSettings,
                ...parsed,
                text: { ...defaultAiSettings.text, ...parsed.text },
                image: { ...defaultAiSettings.image, ...parsed.image },
                vision: { ...defaultAiSettings.vision, ...parsed.vision },
              });
              window.localStorage.removeItem("chengwenfang-ai-settings");
              if (!cancelled) {
                setAiSettings(migrated.settings);
                setAiSettingsUpdatedAt(migrated.updatedAt);
              }
              return;
            } catch {
              return;
            }
          }
        }
        setAiSettings(result.settings);
        setAiSettingsUpdatedAt(result.updatedAt);
      })
      .catch(() => undefined);
    return () => {
      cancelled = true;
    };
  }, []);


  return {
    data,
    setData,
    selectedId,
    setSelectedId,
    activeTab,
    setActiveTab,
    publishingDirty,
    setPublishingDirty,
    activeNav,
    setActiveNav,
    courseModeExpanded,
    setCourseModeExpanded,
    hotNewsModeExpanded,
    setHotNewsModeExpanded,
    contentThemes,
    setContentThemes,
    deletedThemes,
    setDeletedThemes,
    themeDeletedNotice,
    setThemeDeletedNotice,
    hotNewsThemes,
    setHotNewsThemes,
    selectedThemeId,
    setSelectedThemeId,
    selectedHotNewsThemeId,
    setSelectedHotNewsThemeId,
    themeWizardOpen,
    setThemeWizardOpen,
    themeWizardResumeId,
    setThemeWizardResumeId,
    hotNewsThemeWizardOpen,
    setHotNewsThemeWizardOpen,
    hotNewsThemeWizardResumeId,
    setHotNewsThemeWizardResumeId,
    themePickerOpen,
    setThemePickerOpen,
    executionThemePickerOpen,
    setExecutionThemePickerOpen,
    hotNewsThemePickerOpen,
    setHotNewsThemePickerOpen,
    routeCourseId,
    setRouteCourseId,
    routeWorkflowId,
    setRouteWorkflowId,
    routeHotNewsTopicId,
    setRouteHotNewsTopicId,
    routeHotNewsArticleId,
    setRouteHotNewsArticleId,
    routeHotNewsExecutionArticleId,
    setRouteHotNewsExecutionArticleId,
    routeHotNewsCollectThemeId,
    setRouteHotNewsCollectThemeId,
    routeHotNewsResearchTopicId,
    setRouteHotNewsResearchTopicId,
    routeHotNewsSourceManagement,
    setRouteHotNewsSourceManagement,
    routeHotNewsSelected,
    setRouteHotNewsSelected,
    routeHotNewsRadar,
    setRouteHotNewsRadar,
    searchQuery,
    setSearchQuery,
    publishedCourses,
    setPublishedCourses,
    taskRunning,
    setTaskRunning,
    taskMessage,
    setTaskMessage,
    generationSettings,
    setGenerationSettings,
    generationSettingsLoading,
    setGenerationSettingsLoading,
    generationSettingsError,
    setGenerationSettingsError,
    appDialog,
    setAppDialog,
    dialogResolver,
    generationJobs,
    setGenerationJobs,
    selectedGenerationJobId,
    setSelectedGenerationJobId,
    generationEvents,
    setGenerationEvents,
    generationNodes,
    setGenerationNodes,
    generationVersions,
    setGenerationVersions,
    checkedRules,
    setCheckedRules,
    collapsedGroups,
    setCollapsedGroups,
    loadState,
    setLoadState,
    chengwenfangRefreshKey,
    setChengwenfangRefreshKey,
    courseContent,
    setCourseContent,
    articleVersions,
    setArticleVersions,
    draftArticleVersions,
    setDraftArticleVersions,
    humanReviews,
    setHumanReviews,
    aiSettings,
    setAiSettings,
    aiSettingsUpdatedAt,
    setAiSettingsUpdatedAt,
    wechatConfigured,
    setWechatConfigured,
    settingsSectionHint,
    setSettingsSectionHint,
    homeSkillCount,
    setHomeSkillCount,
    homeSelectedTopics,
    setHomeSelectedTopics,
    homeHotArticleCount,
    setHomeHotArticleCount,
    homeWorkflows,
    setHomeWorkflows,
    homeRecentSuccessJobs,
    setHomeRecentSuccessJobs,
    homeJobTitleByEntityId,
    setHomeJobTitleByEntityId,
    homeRecentTasks,
    setHomeRecentTasks,
    refreshCourseSystemThemes
  };
}
