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
  loadWorkbenchData,
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
  type WorkbenchData,
  emptyCourse,
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

export function useWorkbenchPart4(s: Record<string, any>) {
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
    workbenchRefreshKey,
    setWorkbenchRefreshKey,
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
    syncRoute,
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
  } = s;
  useEffect(() => {
    if (activeNav !== "dashboard") return;
    let cancelled = false;
    (async () => {
      try {
        const [skills, workflows, courseJobs] = await Promise.all([
          listSkills().catch(() => [] as Skill[]),
          listWorkflows().catch(() => [] as WorkflowSummary[]),
          listCourseGenerationJobs().catch(() => [] as JobData[]),
        ]);
        if (cancelled) return;
        setHomeSkillCount(skills.length);
        setGenerationJobs(courseJobs);

        // 首页固定关注：雷达采集 / 课程内容 / 热点成文
        const featuredWorkflows = pickHomeFeaturedWorkflows(workflows);
        const workflowBundles = await Promise.all(
          featuredWorkflows.map(async (workflow) => {
            try {
              const runs = await listWorkflowRuns(workflow.id);
              const ordered = [...runs].sort((left, right) =>
                String(right.finishedAt || right.createdAt).localeCompare(
                  String(left.finishedAt || left.createdAt),
                ),
              );
              return { workflow, runs: ordered };
            } catch {
              return { workflow, runs: [] as Awaited<ReturnType<typeof listWorkflowRuns>> };
            }
          }),
        );
        if (cancelled) return;

        const pulses = workflowBundles.map(({ workflow, runs }) => {
          const latest = runs[0] || null;
          const latestSuccess =
            runs.find(
              (run) =>
                run.status === "succeeded" || run.status === "completed",
            ) || null;
          return {
            id: workflow.id,
            key: workflow.key || "",
            scene: workflow.scene || "",
            name: workflow.name || workflow.key,
            status: workflow.status,
            lastRunStatus: latest?.status || null,
            lastRunAt: latest?.finishedAt || latest?.createdAt || null,
            lastSuccessAt:
              latestSuccess?.finishedAt || latestSuccess?.createdAt || null,
            lastSuccessJobId: latestSuccess?.jobId || null,
          };
        });
        setHomeWorkflows(pulses);

        // 三工作流全部 run 合并，取时间最近的 5 条对应执行轨迹
        const mergedRuns = workflowBundles
          .flatMap(({ workflow, runs }) =>
            runs
              .filter((run) => Boolean(run.jobId))
              .map((run) => ({
                jobId: run.jobId,
                workflowKey: workflow.key || "",
                workflowScene: workflow.scene || "",
                workflowName: workflow.name || workflow.key,
                runStatus: run.status,
                at: run.finishedAt || run.createdAt,
              })),
          )
          .sort((left, right) => String(right.at).localeCompare(String(left.at)))
          .slice(0, 5);
        const recentJobs = await Promise.all(
          mergedRuns.map((item) => loadJob(item.jobId).catch(() => null)),
        );
        if (cancelled) return;
        setHomeRecentTasks(
          mergedRuns.map((item, index) => ({
            ...item,
            job: recentJobs[index],
          })),
        );

        const successJobIds = [
          ...new Set(
            pulses
              .map((item) => item.lastSuccessJobId)
              .filter((jobId): jobId is string => Boolean(jobId)),
          ),
        ];
        const successJobs = (
          await Promise.all(
            successJobIds.map((jobId) => loadJob(jobId).catch(() => null)),
          )
        ).filter((job): job is JobData => job != null);
        if (!cancelled) setHomeRecentSuccessJobs(successJobs);
      } catch {
        if (!cancelled) {
          setHomeSkillCount(0);
          setHomeWorkflows([]);
          setHomeRecentTasks([]);
          setHomeRecentSuccessJobs([]);
        }
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [activeNav]);

  // 为最近任务补全课名 / 热点文标题（不依赖当前侧栏主题）
  useEffect(() => {
    if (activeNav !== "dashboard") return;
    const jobs = homeRecentTasks
      .map((item) => item.job)
      .filter((job): job is JobData => job != null);
    if (jobs.length === 0) return;
    let cancelled = false;
    (async () => {
      const next: Record<string, string> = {};
      await Promise.all(
        jobs.map(async (job) => {
          const entityId = job.entityId;
          if (!entityId) return;
          if (homeJobTitleByEntityId[entityId]) {
            next[entityId] = homeJobTitleByEntityId[entityId];
            return;
          }
          const fromCourses = courses.find((course) => course.id === entityId);
          if (fromCourses?.title) {
            next[entityId] = fromCourses.title;
            return;
          }
          const type = job.type || "";
          if (
            type.includes("hot_news_article") ||
            type.includes("hot_article")
          ) {
            try {
              const article = await getHotNewsArticle(entityId, job.documentId);
              const title = (article.title || "").trim();
              if (title) next[entityId] = title;
            } catch {
              /* ignore */
            }
            return;
          }
          if (type === "hot_news_fetch" || type.includes("fetch")) {
            next[entityId] = "热点雷达采集";
            return;
          }
          try {
            const course = await unwrapApiResult<{ title?: string | null }>(
              await fetch(`/api/courses/${encodeURIComponent(entityId)}`, {
                cache: "no-store",
              }),
            );
            if (course.title?.trim()) next[entityId] = course.title.trim();
          } catch {
            /* ignore */
          }
        }),
      );
      if (cancelled) return;
      setHomeJobTitleByEntityId((current) => {
        let changed = false;
        const merged = { ...current };
        for (const [entityId, title] of Object.entries(next)) {
          if (merged[entityId] !== title) {
            merged[entityId] = title;
            changed = true;
          }
        }
        return changed ? merged : current;
      });
    })();
    return () => {
      cancelled = true;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [activeNav, homeRecentTasks, courses]);

  useEffect(() => {
    if (activeNav !== "dashboard") return;
    let cancelled = false;
    const enabledThemes = hotNewsThemes.filter(
      (theme) => theme.status === "enabled",
    );
    if (enabledThemes.length === 0) {
      setHomeSelectedTopics(0);
      setHomeHotArticleCount(0);
      return;
    }
    (async () => {
      try {
        const topicLists = await Promise.all(
          enabledThemes.map((theme) =>
            listHotNewsConfirmedTopics(theme.id).catch(() => []),
          ),
        );
        if (cancelled) return;
        const topics = topicLists.flat();
        setHomeSelectedTopics(topics.length);
        const articleLists = await Promise.all(
          topics.slice(0, 40).map((topic) =>
            listHotNewsArticles(topic.id).catch(() => []),
          ),
        );
        if (!cancelled) {
          setHomeHotArticleCount(articleLists.reduce((sum, list) => sum + list.length, 0));
        }
      } catch {
        if (!cancelled) {
          setHomeSelectedTopics(0);
          setHomeHotArticleCount(0);
        }
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [activeNav, hotNewsThemes]);

  const selectedCourse = useMemo(
    () =>
      visibleCourses.find((course) => course.id === selectedId) ??
      visibleCourses[0] ??
      courses[0] ??
      emptyCourse,
    [courses, visibleCourses, selectedId],
  );
  useEffect(() => {
    if (routeCourseId && courses.some((course) => course.id === routeCourseId))
      setSelectedId(routeCourseId);
  }, [courses, routeCourseId]);
  const courseGroups = useMemo(() => {
    const groups = new Map<string, Course[]>();

    for (const course of visibleCourses) {
      const series = course.series || "未分类";
      groups.set(series, [...(groups.get(series) ?? []), course]);
    }

    return [...groups.entries()].map(([series, groupCourses]) => ({
      series,
      courses: groupCourses,
      completed: groupCourses.filter((course) => course.status === "人工已审核")
        .length,
    }));
  }, [visibleCourses]);

  const selectedThemeMeta = useMemo(() => {
    return (
      contentThemes.find((theme) => theme.id === selectedThemeId) ||
      contentThemes[0] ||
      null
    );
  }, [contentThemes, selectedThemeId]);

  const selectedHotNewsThemeMeta = useMemo(() => {
    return (
      hotNewsThemes.find((theme) => theme.id === selectedHotNewsThemeId) ||
      hotNewsThemes.find((theme) => theme.status === "enabled") ||
      hotNewsThemes[0] ||
      null
    );
  }, [hotNewsThemes, selectedHotNewsThemeId]);

  /** 采集轨迹页按 URL themeId 解析主题，避免与侧栏选中主题不一致。 */
  const collectHotNewsThemeMeta = useMemo(() => {
    if (!routeHotNewsCollectThemeId) {
      return null;
    }
    return (
      hotNewsThemes.find((theme) => theme.id === routeHotNewsCollectThemeId) ||
      null
    );
  }, [hotNewsThemes, routeHotNewsCollectThemeId]);

  const hotNewsThemeSlug = useMemo(() => {
    return themePathSlug(selectedHotNewsThemeMeta?.code || "ai_frontier");
  }, [selectedHotNewsThemeMeta]);

  return {
    ...s,
    selectedCourse,
    courseGroups,
    selectedThemeMeta,
    selectedHotNewsThemeMeta,
    collectHotNewsThemeMeta,
    hotNewsThemeSlug
  };
}
