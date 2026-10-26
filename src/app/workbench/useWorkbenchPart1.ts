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

export function useWorkbenchPart1(s: Record<string, any>) {
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
    refreshCourseSystemThemes
  } = s;
  useEffect(() => {
    if (!selectedId) return;
    let cancelled = false;
    setGenerationSettingsLoading(true);
    setGenerationSettingsError("");
    loadCourseGenerationSettings(selectedId)
      .then((settings) => {
        if (!cancelled) setGenerationSettings(settings);
      })
      .catch((error) => {
        if (!cancelled) {
          setGenerationSettings(null);
          setGenerationSettingsError(
            error instanceof Error ? error.message : "生成配置加载失败",
          );
        }
      })
      .finally(() => {
        if (!cancelled) setGenerationSettingsLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [selectedId]);




  function showNotice(title: string, message: string) {
    dialogResolver.current = null;
    setAppDialog({ title, message, mode: "notice" });
  }
  function confirmAction(
    title: string,
    message: string,
    tone: "default" | "danger" = "default",
  ) {
    return new Promise<boolean>((resolve) => {
      dialogResolver.current = (result) => resolve(result === true);
      setAppDialog({ title, message, mode: "confirm", tone });
    });
  }

  return {
    ...s,
    showNotice,
    confirmAction
  };
}
