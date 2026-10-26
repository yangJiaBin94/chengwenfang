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

export function useWorkbenchPart5(s: Record<string, any>) {
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
    hotNewsThemeSlug
  } = s;
  const collectHotNewsThemeSlug = useMemo(() => {
    return themePathSlug(
      collectHotNewsThemeMeta?.code ||
        selectedHotNewsThemeMeta?.code ||
        "ai_frontier",
    );
  }, [collectHotNewsThemeMeta, selectedHotNewsThemeMeta]);

  const hotNewsDefaultSearchQueries = useMemo(() => {
    return parseHotNewsThemeSearchQueries(
      selectedHotNewsThemeMeta?.briefingJson,
      selectedHotNewsThemeMeta?.defaultsJson,
    );
  }, [selectedHotNewsThemeMeta]);

  const hotNewsDefaultGithubQuery = useMemo(() => {
    return parseHotNewsThemeGithubQuery(
      selectedHotNewsThemeMeta?.briefingJson,
      selectedHotNewsThemeMeta?.defaultsJson,
    );
  }, [selectedHotNewsThemeMeta]);

  const themeRoutePath = useMemo(() => {
    const code =
      selectedThemeMeta?.code ||
      data?.workspace?.themeCode ||
      "ai_self_study_zero";
    return `/modes/course-system/themes/${themePathSlug(code)}/route`;
  }, [selectedThemeMeta, data?.workspace?.themeCode]);


  /** 按 job 关联课程切到对应主题，保证执行轨迹列表能解析课名。 */

  /** 按采集 job 反查热点主题 code，用于跳转采集轨迹页。 */

  /** 打开课程内容执行轨迹。 */



  /**
   * 首页工作流/任务入口：按场景跳到采集轨迹、热点成文轨迹或课程轨迹。
   */

  /** 最近生成成功 → 课程/热点文章工作区。 */

  useEffect(() => {
    let cancelled = false;
    if (!selectedCourse.id) {
      setCourseContent({
        draft: "",
        article: "",
        draftArticleId: null,
        articleArticleId: null,
        draftDocumentId: null,
        articleDocumentId: null,
        images: [],
        loading: false,
      });
      setArticleVersions([]);
      setDraftArticleVersions([]);
      return;
    }

    /** Load content and version history for the currently selected course. */
    async function loadSelectedCourseContent() {
      // Load content and version history together so tabs switch to one consistent snapshot.
      setCourseContent({
        draft: "",
        article: "",
        draftArticleId: null,
        articleArticleId: null,
        draftDocumentId: null,
        articleDocumentId: null,
        images: [],
        loading: true,
      });
      const content = await loadCourseContent(selectedCourse.id, selectedThemeId);
      const articleDocumentId = preferCourseDocumentId(
        content.articleDocumentId,
        content.articleArticleId,
      );
      const draftDocumentId = preferCourseDocumentId(
        content.draftDocumentId,
        content.draftArticleId,
      );
      const [versions, draftVersions] = await Promise.all([
        content.articleArticleId
          ? loadArticleVersions(content.articleArticleId, articleDocumentId)
          : [],
        content.draftArticleId
          ? loadArticleVersions(content.draftArticleId, draftDocumentId)
          : [],
      ]);
      const review = content.articleArticleId
        ? await loadHumanReview(content.articleArticleId, articleDocumentId)
        : null;
      if (!cancelled) {
        setCourseContent({
          draft: content.draft,
          article: content.article,
          draftArticleId: content.draftArticleId,
          articleArticleId: content.articleArticleId,
          draftDocumentId,
          articleDocumentId,
          images: (content.images ?? []).map((image) => ({
            name: image.name,
            path: image.url || image.path,
            articleId: selectedCourse.id,
            articleTitle: selectedCourse.title,
            articlePath: "",
          })),
          loading: false,
        });
        setArticleVersions(versions);
        setDraftArticleVersions(draftVersions);
        setHumanReviews((current) => {
          const next = { ...current };
          if (review) {
            next[selectedCourse.id] = review;
          } else {
            delete next[selectedCourse.id];
          }
          return next;
        });
      }
    }

    loadSelectedCourseContent().catch(() => {
      if (!cancelled) {
        setCourseContent((current) => ({ ...current, loading: false }));
        setArticleVersions([]);
        setDraftArticleVersions([]);
      }
    });
    return () => {
      cancelled = true;
    };
  }, [selectedCourse.id, selectedThemeId, workbenchRefreshKey, routeCourseId]);

  const syncedImages = useMemo(() => {
    const fromContent =
      courseContent.images?.map((asset) => ({
        src: asset.path,
        alt: asset.name,
        source: "配套图片" as const,
      })) ?? [];
    const assetImages =
      selectedCourse.imageAssets?.map((asset) => ({
        src: asset.path,
        alt: asset.name,
        source: "配套图片" as const,
      })) ?? [];
    const legacyCourseImages =
      selectedCourse.imagePublicPaths?.map((src) => ({
        src,
        alt: imageNameFromSrc(src),
        source: "配套图片" as const,
      })) ?? [];
    const courseImages = fromContent.length
      ? fromContent
      : assetImages.length
        ? assetImages
        : legacyCourseImages;

    return uniqueImages([
      ...courseImages,
      ...extractMarkdownImages(courseContent.draft, "课程原稿"),
      ...extractMarkdownImages(courseContent.article, "公众号文章"),
    ]);
  }, [
    courseContent.article,
    courseContent.draft,
    courseContent.images,
    selectedCourse.imageAssets,
    selectedCourse.imagePublicPaths,
  ]);


  /** Show temporary task feedback for production actions not implemented yet. */









  /** Queue an image generation job and refresh course assets after it commits. */

  /** Delete a generated asset and refresh both article bodies and course metadata. */

  /** Toggle one presentation-only sidebar checklist item. */

  /** Expand or collapse one course series without affecting the others. */

  /** Optimistically save a versioned draft or article edit. */

  /** Restore the initial imported content as a new current version. */

  /** Restore a selected article snapshot as a new version. */

  /** Synchronize a backend-committed repair into React state and version history. */

  /** Persist AI settings before updating the page's saved timestamp. */

  /** Persist the current human decision through the version-bound backend API. */

  /** Withdraw the current version decision and restore the prior course status. */
  function closeAppDialog(result: boolean | string | null = false) {
    dialogResolver.current?.(result);
    dialogResolver.current = null;
    setAppDialog(null);
  }
  function promptAction(
    title: string,
    message: string,
    options: {
      inputLabel?: string;
      placeholder?: string;
      minLength?: number;
      tone?: "default" | "danger";
    } = {},
  ) {
    return new Promise<string | null>((resolve) => {
      dialogResolver.current = (result) =>
        resolve(typeof result === "string" ? result : null);
      setAppDialog({
        title,
        message,
        mode: "prompt",
        inputLabel: options.inputLabel,
        placeholder: options.placeholder,
        minLength: options.minLength,
        tone: options.tone,
      });
    });
  }
  async function changeDetailTab(next: TabKey) {
    if (next === activeTab) return;
    if (activeTab === "mdnice" && publishingDirty) {
      const leave = await confirmAction(
        "离开公众号排版？",
        "文章内容有未保存修改。离开后这些修改会丢失。",
        "danger",
      );
      if (!leave) return;
    }
    setPublishingDirty(false);
    setActiveTab(next);
  }
  function selectGenerationJob(jobId: string) {
    setGenerationEvents([]);
    setGenerationNodes([]);
    setGenerationVersions([]);
    setSelectedGenerationJobId(jobId);
  }
  function mergeGenerationEvent(event: JobEventData) {
    setGenerationEvents((current) =>
      [...current.filter((item) => item.id !== event.id), event].sort(
        (a, b) => a.id - b.id,
      ),
    );
    setTaskMessage(event.message);
  }
  async function togglePublished(courseId: string) {
    const nextValue = !publishedCourses[courseId];
    await setCoursePublished(courseId, nextValue);
    setPublishedCourses((current) => ({ ...current, [courseId]: nextValue }));
    setData((current) => ({
      ...current,
      courses: current.courses.map((course) =>
        course.id === courseId ? { ...course, published: nextValue } : course,
      ),
    }));
  }
  function themeCoursePath(courseId: string) {
    const code =
      selectedThemeMeta?.code ||
      data?.workspace?.themeCode ||
      "ai_self_study_zero";
    return `/modes/course-system/themes/${themePathSlug(code)}/courses/${encodeURIComponent(courseId)}`;
  }

  return {
    ...s,
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
  };
}
