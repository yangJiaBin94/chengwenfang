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

export function useChengwenfangPart3(s: Record<string, any>) {
  const {
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
    refreshCourseSystemThemes,
    showNotice,
    confirmAction,
    syncRoute
  } = s;
  async function alignExecutionThemeForJob(job: JobData) {
    try {
      const course = await unwrapApiResult<{ themeId?: string | null }>(
        await fetch(`/api/courses/${encodeURIComponent(job.entityId)}`, {
          cache: "no-store",
        }),
      );
      if (course.themeId) {
        setSelectedThemeId((current) =>
          current === course.themeId ? current : course.themeId!,
        );
      }
    } catch {
      /* 主题对齐失败时仍展示任务 */
    }
  }
  const { navigate } = useRouter({
    syncRoute,
    syncDeps: [
      contentThemes,
      deletedThemes,
      hotNewsThemes,
      selectedHotNewsThemeId,
    ],
    isMdniceTab: activeTab === "mdnice",
    isPublishingDirty: publishingDirty,
    confirmLeave: confirmAction,
    clearPublishingDirty: () => setPublishingDirty(false),
  });




  /** Select one execution record without briefly displaying another task's event history. */


  async function refreshGenerationJobs(preferredId?: string) {

    const jobs = await listCourseGenerationJobs();
    setGenerationJobs(jobs);
    setSelectedGenerationJobId(
      (current) => preferredId || current || jobs[0]?.id || null,
    );
  }
  useEffect(() => {
    if (activeNav !== "executions") return;
    void refreshGenerationJobs();
  }, [activeNav, chengwenfangRefreshKey]);

  useEffect(() => {
    if (activeNav !== "executions" || !selectedGenerationJobId) {
      setGenerationEvents([]);
      setGenerationNodes([]);
      setGenerationVersions([]);
      return;
    }
    // Clear synchronously before loading so a slow request cannot leave the previous run visible.
    setGenerationEvents([]);
    setGenerationNodes([]);
    setGenerationVersions([]);
    let cancelled = false;
    const requestedJobId = selectedGenerationJobId;
    loadJobEventHistory(requestedJobId)
      .then((events) => {
        if (!cancelled) setGenerationEvents(events);
      })
      .catch(() => {
        if (!cancelled) setGenerationEvents([]);
      });
    loadJobNodes(requestedJobId)
      .then((nodes) => {
        if (!cancelled) setGenerationNodes(nodes);
      })
      .catch(() => {
        if (!cancelled) setGenerationNodes([]);
      });
    loadJobArticleVersions(requestedJobId)
      .then((versions) => {
        if (!cancelled) setGenerationVersions(versions);
      })
      .catch(() => {
        if (!cancelled) setGenerationVersions([]);
      });
    return () => {
      cancelled = true;
    };
  }, [activeNav, selectedGenerationJobId]);

  usePolling(
    async () => {
      const requestedJobId = selectedGenerationJobId!;
      /** Keep an open trajectory page synchronized even after refreshes or SSE disconnects. */
      const [jobsResult, jobResult, eventsResult, nodesResult] =
        await Promise.allSettled([
          listCourseGenerationJobs(),
          loadJob(requestedJobId),
          loadJobEventHistory(requestedJobId),
          loadJobNodes(requestedJobId),
        ]);
      const latest =
        jobResult.status === "fulfilled" ? jobResult.value : null;
      if (jobsResult.status === "fulfilled") {
        const jobs = jobsResult.value;
        setGenerationJobs(
          latest
            ? jobs.some((job) => job.id === latest.id)
              ? jobs.map((job) => (job.id === latest.id ? latest : job))
              : [latest, ...jobs]
            : jobs,
        );
      } else if (latest) {
        setGenerationJobs((current) =>
          current.some((job) => job.id === latest.id)
            ? current.map((job) => (job.id === latest.id ? latest : job))
            : [latest, ...current],
        );
      }
      if (eventsResult.status === "fulfilled") {
        setGenerationEvents(eventsResult.value);
      }
      if (nodesResult.status === "fulfilled") {
        setGenerationNodes(nodesResult.value);
      }
    },
    2_000,
    [activeNav, selectedGenerationJobId],
    {
      enabled: activeNav === "executions" && Boolean(selectedGenerationJobId),
    },
  );

  useEffect(() => {
    let cancelled = false;
    setLoadState("loading");

    loadChengwenfangData<ChengwenfangData>(selectedThemeId)
      .then(async (payload) => {
        try {
          await migrateLegacyBrowserContent(payload.courses);
          await migrateLegacyHumanReviews(payload.courses);
        } catch {
          // Legacy migration can be retried on the next load without blocking database content.
        }
        if (cancelled) return;
        setData(payload);
        // ????????????????????????????
        setSelectedId((current: string) =>
          payload.courses.some((course) => course.id === current)
            ? current
            : (payload.courses[0]?.id ?? ""),
        );
        setLoadState("ready");
      })
      .catch((error: unknown) => {
        if (cancelled) return;
        const message = error instanceof Error ? error.message : "";
        if (message.includes("主题已删除")) {
          setThemeDeletedNotice(message);
        }
        setData((current) => ({
          ...current,
          courses: [],
          metrics: {
            courseCount: 0,
            reviewedCount: 0,
            pendingCount: 0,
            imageCount: 0,
          },
        }));
        setSelectedId("");
        setLoadState("ready");
      });
    return () => {
      cancelled = true;
    };
  }, [selectedThemeId, chengwenfangRefreshKey]);

  useEffect(() => {
    const onShortcut = (event: KeyboardEvent) => {
      if (
        event.key === "Escape" &&
        document.activeElement?.matches(".search-box input")
      ) {
        setSearchQuery("");
        return;
      }
      if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === "k") {
        event.preventDefault();
        document.querySelector<HTMLInputElement>(".search-box input")?.focus();
      }
    };
    window.addEventListener("keydown", onShortcut);
    return () => window.removeEventListener("keydown", onShortcut);
  }, []);

  const courses = data.courses;
  useEffect(() => {
    setPublishedCourses((current) => {
      const next = { ...current };
      courses.forEach((course) => {
        if (
          typeof (course as Course & { published?: boolean }).published ===
          "boolean"
        )
          next[course.id] = Boolean(
            (course as Course & { published?: boolean }).published,
          );
      });
      return next;
    });
  }, [courses]);
  const visibleCourses = useMemo(() => {
    const query = searchQuery.trim().toLocaleLowerCase();
    return courses.filter((course) => {
      const matchesQuery =
        !query ||
        [course.title, course.summary, course.series, course.status]
          .join(" ")
          .toLocaleLowerCase()
          .includes(query);
      const matchesView =
        activeNav === "review"
          ? course.status !== "人工已审核"
          : activeNav === "wechat"
            ? course.articleWords > 0 ||
              course.status.includes("公众号") ||
              course.status.includes("排版")
            : true;
      return matchesQuery && matchesView;
    });
  }, [activeNav, courses, searchQuery]);
  const activeCourseThemeCount = contentThemes.filter(
    (theme) => theme.status === "enabled" || theme.status === "draft",
  ).length;
  const courseSeriesCount = contentThemes.reduce(
    (sum, theme) => sum + (theme.seriesCount || 0),
    0,
  );
  const hotNewsEnabledCount = hotNewsThemes.filter(
    (theme) => theme.status === "enabled",
  ).length;
  const enabledThemeTotal =
    contentThemes.filter((theme) => theme.status === "enabled").length +
    hotNewsEnabledCount;
  const courseArticleCount = courses.filter(
    (course) => course.articleWords > 0,
  ).length;
  const totalArticleCount = courseArticleCount + homeHotArticleCount;
  const failedJobCount = generationJobs.filter(
    (job) => job.status === "failed",
  ).length;
  const activeJobCount = generationJobs.filter(
    (job) => job.status === "pending" || job.status === "running",
  ).length;
  const aiReady = Boolean(
    aiSettings.text.apiKeyConfigured || aiSettings.text.apiKey.trim(),
  );
  const recentSuccessArticles = useMemo(() => {
    const byId = new Map<string, JobData>();
    for (const job of homeRecentSuccessJobs) {
      if (isHomeArticleSuccessJob(job)) byId.set(job.id, job);
    }
    for (const job of generationJobs) {
      if (isHomeArticleSuccessJob(job)) byId.set(job.id, job);
    }
    return [...byId.values()]
      .sort((left, right) =>
        String(right.finishedAt || right.createdAt).localeCompare(
          String(left.finishedAt || left.createdAt),
        ),
      )
      .slice(0, 3);
  }, [generationJobs, homeRecentSuccessJobs]);
  const primaryHotNewsTheme = useMemo(
    () =>
      hotNewsThemes.find((theme) => theme.status === "enabled") ||
      hotNewsThemes[0] ||
      null,
    [hotNewsThemes],
  );

  // 首页汇总：Skill、三个主工作流状态、最近 5 条执行、最近成功成文

  return {
    ...s,
    alignExecutionThemeForJob,
    navigate,
    refreshGenerationJobs,
    courses,
    visibleCourses,
    activeCourseThemeCount,
    courseSeriesCount,
    hotNewsEnabledCount,
    enabledThemeTotal,
    courseArticleCount,
    totalArticleCount,
    failedJobCount,
    activeJobCount,
    aiReady,
    recentSuccessArticles,
    primaryHotNewsTheme
  };
}
