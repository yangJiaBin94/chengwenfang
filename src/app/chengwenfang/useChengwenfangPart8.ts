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

export function useChengwenfangPart8(s: Record<string, any>) {
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
    themeCoursePath,
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
    openMdniceTask,
    runImageTask,
    removeCourseImage,
    toggleRule,
    toggleCourseGroup,
    updateCourseContent,
    resetCourseContent,
    restoreCourseVersion
  } = s;
  async function applyRepairResult(article: ArticleData) {
    // The backend already committed the repair; only synchronize React state and version history.
    setCourseContent((current) => ({ ...current, article: article.content }));
    setData((current) => ({
      ...current,
      courses: current.courses.map((course) =>
        course.id === selectedCourse.id
          ? {
              ...course,
              articleWords: article.wordCount,
              updatedAt: article.updatedAt,
            }
          : course,
      ),
    }));
    setArticleVersions(
      await loadArticleVersions(
        article.id,
        preferCourseDocumentId(article.documentId, article.id),
      ),
    );
    setHumanReviews((current) => {
      const next = { ...current };
      delete next[selectedCourse.id];
      return next;
    });
  }
  async function saveAiSettings(settings: AiSettings) {
    // Wait for the database transaction before updating the settings page timestamp.
    const saved = await saveAiSettingsToDatabase(settings);
    setAiSettings(saved.settings);
    setAiSettingsUpdatedAt(saved.updatedAt);
    return saved.updatedAt;
  }
  async function submitHumanReview(
    status: Exclude<HumanReviewStatus, "pending">,
    comment: string,
  ) {
    if (!courseContent.articleArticleId)
      throw new Error("当前公众号文章不存在");
    const review = await saveHumanReview(
      courseContent.articleArticleId,
      status,
      comment.trim(),
      preferCourseDocumentId(
        courseContent.articleDocumentId,
        courseContent.articleArticleId,
      ),
    );
    setHumanReviews((current) => ({ ...current, [selectedCourse.id]: review }));
    setData((current) => ({
      ...current,
      courses: current.courses.map((course) =>
        course.id === selectedCourse.id
          ? {
              ...course,
              status: status === "approved" ? "人工已审核" : "人工已退回",
              progress:
                status === "approved" ? 100 : Math.min(course.progress, 92),
              updatedAt: review.reviewedAt,
            }
          : course,
      ),
    }));
  }
  async function resetHumanReview() {
    if (!courseContent.articleArticleId) return;
    await resetHumanReviewApi(
      courseContent.articleArticleId,
      preferCourseDocumentId(
        courseContent.articleDocumentId,
        courseContent.articleArticleId,
      ),
    );
    setHumanReviews((current) => {
      const next = { ...current };
      delete next[selectedCourse.id];
      return next;
    });
    setData((current) => ({
      ...current,
      courses: current.courses.map((course) =>
        course.id === selectedCourse.id
          ? {
              ...course,
              status: course.reviewScore > 0 ? "AI 已检查" : "已排版",
              progress: course.reviewScore > 0 ? 86 : 78,
              updatedAt: "审核已撤销",
            }
          : course,
      ),
    }));
  }

  return {
    ...s,
    applyRepairResult,
    saveAiSettings,
    submitHumanReview,
    resetHumanReview
  };
}
