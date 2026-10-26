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

export function useWorkbenchPart6(s: Record<string, any>) {
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
    primaryHotNewsTheme,
    selectedCourse,
    courseGroups,
    selectedThemeMeta,
    selectedHotNewsThemeMeta,
    collectHotNewsThemeMeta,
    hotNewsThemeSlug,
    collectHotNewsThemeSlug,
    hotNewsDefaultSearchQueries,
    hotNewsDefaultGithubQuery,
    themeRoutePath,
    syncedImages,
    closeAppDialog,
    promptAction,
    changeDetailTab,
    selectGenerationJob,
    mergeGenerationEvent,
    togglePublished,
    themeCoursePath
  } = s;
  async function resolveRadarCollectThemeSlug(job: JobData) {
    const themes =
      hotNewsThemes.length > 0
        ? hotNewsThemes
        : primaryHotNewsTheme
          ? [primaryHotNewsTheme]
          : [];
    for (const theme of themes) {
      const collectJobs = await listHotNewsRadarCollectRuns(theme.id).catch(
        () => [] as JobData[],
      );
      if (collectJobs.some((item) => item.id === job.id)) {
        return themePathSlug(theme.code);
      }
      const fetchRuns = await listHotNewsRuns(theme.id).catch(() => []);
      if (
        fetchRuns.some(
          (run) =>
            run.id === job.entityId ||
            String((run as { jobId?: string }).jobId || "") === job.id,
        )
      ) {
        return themePathSlug(theme.code);
      }
    }
    return themePathSlug(
      primaryHotNewsTheme?.code ||
        selectedHotNewsThemeMeta?.code ||
        "ai_frontier",
    );
  }
  async function openExecutionTrajectory(jobId: string) {
    try {
      const job = await loadJob(jobId);
      setGenerationJobs((current) =>
        current.some((item) => item.id === job.id)
          ? current.map((item) => (item.id === job.id ? job : item))
          : [job, ...current],
      );
      await alignExecutionThemeForJob(job);
    } catch {
      /* 仍跳转，由轨迹页按 jobId 拉取 */
    }
    setSelectedGenerationJobId(jobId);
    await navigate(`/executions?job=${encodeURIComponent(jobId)}`);
  }
  async function openHomeWorkflowTrajectory(options: {
    jobId: string;
    key?: string;
    scene?: string;
  }) {
    const { jobId, key = "", scene = "" } = options;
    const marker = `${scene} ${key}`.toLowerCase();
    try {
      const job = await loadJob(jobId);
      const isRadarCollect =
        job.type === "hot_news_fetch" ||
        marker.includes(WorkflowFeatureKey.HOT_NEWS_RADAR_COLLECT) ||
        marker.includes("radar_collect") ||
        marker.includes("hot_news_fetch");
      if (isRadarCollect) {
        const themeSlug = await resolveRadarCollectThemeSlug(job);
        await navigate(
          `/modes/hot-news/themes/${encodeURIComponent(themeSlug)}/collect-execution?job=${encodeURIComponent(jobId)}`,
        );
        return;
      }
      const isHotArticle =
        job.type.includes("hot_news_article") ||
        job.type.includes("hot_article") ||
        marker.includes(WorkflowFeatureKey.HOT_NEWS_ARTICLE_GENERATION);
      if (isHotArticle) {
        await navigate(
          withDocumentIdQuery(
            `/modes/hot-news/articles/${encodeURIComponent(job.entityId)}/execution?job=${encodeURIComponent(jobId)}`,
            job.documentId,
          ),
        );
        return;
      }
    } catch {
      /* 类型未知时回落到课程轨迹 */
    }
    await openExecutionTrajectory(jobId);
  }
  async function openHomeArticleWorkspace(job: JobData) {
    const type = job.type || "";
    if (
      type.includes("hot_news_article") ||
      type.includes("hot_article") ||
      (type.includes("hot") && job.entityType === "article")
    ) {
      try {
        const article = await getHotNewsArticle(job.entityId, job.documentId);
        if (article.topicId) {
          await navigate(
            withDocumentIdQuery(
              `/modes/hot-news/topics/${encodeURIComponent(article.topicId)}/articles/${encodeURIComponent(job.entityId)}`,
              article.documentId || job.documentId,
            ),
          );
          return;
        }
      } catch {
        /* fall through */
      }
      await navigate(
        withDocumentIdQuery(
          `/modes/hot-news/articles/${encodeURIComponent(job.entityId)}/execution`,
          job.documentId,
        ),
      );
      return;
    }
    let slug =
      selectedThemeMeta?.code ||
      data?.workspace?.themeCode ||
      "ai_self_study_zero";
    try {
      const course = await unwrapApiResult<{ themeId?: string | null }>(
        await fetch(`/api/courses/${encodeURIComponent(job.entityId)}`, {
          cache: "no-store",
        }),
      );
      if (course.themeId) {
        const theme = contentThemes.find((item) => item.id === course.themeId);
        slug = theme ? theme.code : course.themeId;
      }
    } catch {
      /* keep fallback slug */
    }
    await navigate(
      `/modes/course-system/themes/${themePathSlug(slug)}/courses/${encodeURIComponent(job.entityId)}`,
    );
  }
  function runMockTask() {
    setTaskRunning(false);
    setTaskMessage(
      "该生产动作尚未配置后端工作流，请先完成 AI 设置并接入对应任务接口。",
    );
  }
  async function runGeneration(kind: "course_draft" | "wechat_article") {
    const articleId =
      kind === "course_draft"
        ? courseContent.draftArticleId
        : courseContent.articleArticleId;
    const documentId =
      kind === "course_draft"
        ? courseContent.draftDocumentId
        : courseContent.articleDocumentId;
    if (!articleId) {
      setTaskMessage("当前课程尚未建立文章记录，请先完成内容导入。");
      return;
    }
    setTaskRunning(true);
    try {
      await requestContentGeneration(
        articleId,
        kind,
        (event) => setTaskMessage(event.message),
        preferCourseDocumentId(documentId, articleId),
      );
      setTaskMessage("内容生成完成，正在刷新工作台。");
      setWorkbenchRefreshKey((current) => current + 1);
      setActiveTab(kind === "course_draft" ? "draft" : "article");
    } catch (error) {
      setTaskMessage(error instanceof Error ? error.message : "内容生成失败");
    } finally {
      setTaskRunning(false);
    }
  }
  async function runCourseContentGenerationFor(course: Course) {
    if (!generationSettings || generationSettings.courseId !== course.id) {
      setTaskMessage("请等待字数与插图推荐加载完成");
      return;
    }
    let savedSettings: CourseGenerationSettingsData;
    try {
      savedSettings = await saveCourseGenerationSettings(course.id, {
        wordCountOption: generationSettings.wordCountOption,
        imageCountOption: generationSettings.imageCountOption,
      });
      setGenerationSettings(savedSettings);
    } catch (error) {
      setTaskMessage(
        error instanceof Error ? error.message : "生成配置保存失败",
      );
      return;
    }
    const confirmed = await confirmAction(
      "确认生成课程内容",
      `正文字数：${wordCountLabels[savedSettings.wordCountOption]}\n正文插图：${imageCountLabels[savedSettings.imageCountOption]}\n封面：另生成 1 张，不计入正文插图`,
    );
    if (!confirmed) return;
    setTaskRunning(true);
    setTaskMessage("生成课程内容任务已排队");
    setGenerationEvents([]);
    let queuedJobId: string | undefined;
    try {
      // 点击时反查「生成课程内容」工作流；停用则提示并中止
      await assertWorkflowFeatureAvailable(
        WorkflowFeatureKey.COURSE_CONTENT_GENERATION,
      );
      const result = await requestCourseContentGeneration(
        course.id,
        {
          wordCountOption: savedSettings.wordCountOption,
          imageCountOption: savedSettings.imageCountOption,
        },
        mergeGenerationEvent,
        (job) => {
          queuedJobId = job.id;
          selectGenerationJob(job.id);
          navigate(
            `/executions?job=${encodeURIComponent(job.id)}&course=${encodeURIComponent(course.id)}`,
          );
          void refreshGenerationJobs(job.id);
        },
      );
      setTaskMessage(
        result?.partial
          ? "课程内容已完成，部分图片待补生成"
          : "课程内容生成完成",
      );
      setSelectedId(course.id);
      setActiveTab("article");
      setWorkbenchRefreshKey((current) => current + 1);
      await navigate(themeCoursePath(course.id));
    } catch (error) {
      setTaskMessage(
        error instanceof Error ? error.message : "课程内容生成失败",
      );
    } finally {
      // SSE reports the terminal event, while this refresh commits the matching job status to the UI.
      await refreshGenerationJobs(queuedJobId).catch(() => undefined);
      setTaskRunning(false);
    }
  }
  async function openVersionTrajectory(
    courseId: string,
    workflowRunId: string,
  ) {
    const jobs = await listCourseGenerationJobs(courseId);
    const job = jobs.find(
      (item) =>
        item.workflowRunId === workflowRunId ||
        item.output?.workflowRunId === workflowRunId,
    );
    if (!job) {
      showNotice("无法查看生成轨迹", "未找到该版本对应的课程生成轨迹。");
      return;
    }
    setGenerationJobs(jobs);
    setSelectedGenerationJobId(job.id);
    navigate(
      `/executions?job=${encodeURIComponent(job.id)}&course=${encodeURIComponent(courseId)}`,
    );
  }
  async function openLatestCourseTrajectory(courseId: string) {
    const jobs = await listCourseGenerationJobs(courseId);
    if (!jobs.length) {
      showNotice("暂无执行记录", "当前课程还没有生成执行记录。");
      return;
    }
    const currentJob =
      jobs.find((job) => job.id === selectedGenerationJobId) || jobs[0];
    setGenerationJobs(jobs);
    setSelectedGenerationJobId(currentJob.id);
    navigate(
      `/executions?job=${encodeURIComponent(currentJob.id)}&course=${encodeURIComponent(courseId)}`,
    );
  }
  async function runCourseContentGeneration() {
    await runCourseContentGenerationFor(selectedCourse);
  }
  function openMdniceTask() {
    window.open("https://editor.mdnice.com/", "_blank", "noopener,noreferrer");
    setTaskMessage("已打开 mdnice 编辑器，请先复制文章 Markdown 再完成排版。");
  }

  return {
    ...s,
    resolveRadarCollectThemeSlug,
    openExecutionTrajectory,
    openHomeWorkflowTrajectory,
    openHomeArticleWorkspace,
    runMockTask,
    runGeneration,
    runCourseContentGenerationFor,
    openVersionTrajectory,
    openLatestCourseTrajectory,
    runCourseContentGeneration,
    openMdniceTask
  };
}
