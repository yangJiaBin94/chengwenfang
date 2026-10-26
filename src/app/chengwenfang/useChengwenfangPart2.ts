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

export function useChengwenfangPart2(s: Record<string, any>) {
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
    confirmAction
  } = s;
  function syncRoute() {
    const path = window.location.pathname;
    // 切换侧栏/路由时收起主题下拉，避免跨页残留

    setHotNewsThemePickerOpen(false);
    setThemePickerOpen(false);
    setRouteHotNewsTopicId(null);
    setRouteHotNewsArticleId(null);
    setRouteHotNewsExecutionArticleId(null);
    setRouteHotNewsCollectThemeId(null);
    setRouteHotNewsResearchTopicId(null);
    setRouteHotNewsSourceManagement(false);
    setRouteHotNewsSelected(false);
    setRouteHotNewsRadar(false);
    setHotNewsThemeWizardOpen(false);
    setHotNewsThemeWizardResumeId(null);

    // 旧热点工作区路径：改写 URL 并直接打开对应视图（不走主题启停校验）
    const legacyView = matchLegacyHotNewsWorkspacePath(path);
    if (legacyView) {
      setHotNewsModeExpanded(true);
      const fallbackCode =
        hotNewsThemes.find((theme) => theme.id === selectedHotNewsThemeId)
          ?.code ||
        hotNewsThemes.find((theme) => theme.status === "enabled")?.code ||
        "ai_frontier";
      window.history.replaceState(
        {},
        "",
        buildHotNewsWorkspacePath(fallbackCode, legacyView),
      );
      setRouteHotNewsRadar(legacyView === "radar");
      setRouteHotNewsSelected(legacyView === "selected");
      setRouteHotNewsSourceManagement(legacyView === "sources");
      setRouteHotNewsResearchTopicId(null);
      setRouteHotNewsTopicId(null);
      setRouteHotNewsArticleId(null);
      setRouteWorkflowId(null);
      setRouteCourseId(null);
      setActiveNav("hot-news");
      return;
    }

    const route = parseRoute(path, window.location.search);
    const { page, params } = route;

    if (page === "course-theme-wizard") {
      setCourseModeExpanded(true);
      setRouteCourseId(null);
      setRouteWorkflowId(null);
      setThemeWizardOpen(true);
      setThemeWizardResumeId(
        params.themeSlug ? themeCodeFromSlug(params.themeSlug) : null,
      );
      setActiveNav("mode");
      return;
    }

    if (page === "course-detail" && params.themeSlug && params.courseId) {
      setCourseModeExpanded(true);
      setThemeWizardOpen(false);
      const slug = params.themeSlug;
      const deletedMatch = findThemeBySlug(deletedThemes, slug);
      if (deletedMatch) {
        setThemeDeletedNotice(
          `「${deletedMatch.name}」已删除，课程路线与文章不再展示。可在回收站恢复。`,
        );
        setRouteCourseId(null);
        setRouteWorkflowId(null);
        setActiveNav("recycle-bin");
      } else {
        setThemeDeletedNotice("");
        setSelectedThemeId((current) => {
          const matched = findThemeBySlug(contentThemes, slug);
          return matched?.id || current;
        });
        setRouteCourseId(params.courseId);
        setRouteWorkflowId(null);
        setActiveNav("learning");
      }
      return;
    }

    if (page === "course-route") {
      setCourseModeExpanded(true);
      setThemeWizardOpen(false);
      if (params.themeSlug) {
        const slug = params.themeSlug;
        const deletedMatch = findThemeBySlug(deletedThemes, slug);
        if (deletedMatch) {
          setThemeDeletedNotice(
            `「${deletedMatch.name}」已删除，课程路线与文章不再展示。可在回收站恢复。`,
          );
          setRouteCourseId(null);
          setRouteWorkflowId(null);
          setActiveNav("recycle-bin");
        } else {
          setThemeDeletedNotice("");
          setSelectedThemeId((current) => {
            const matched = findThemeBySlug(contentThemes, slug);
            return matched?.id || current;
          });
          setRouteCourseId(null);
          setRouteWorkflowId(null);
          setActiveNav("learning");
        }
      } else {
        setRouteCourseId(null);
        setRouteWorkflowId(null);
        setActiveNav("learning");
      }
      return;
    }

    if (page === "course-detail" && params.courseId) {
      // 旧路径 /courses/:courseId：URL 不改写
      setCourseModeExpanded(true);
      setThemeWizardOpen(false);
      setRouteCourseId(params.courseId);
      setRouteWorkflowId(null);
      setActiveNav("learning");
      return;
    }

    if (page === "workflows") {
      setRouteWorkflowId(params.workflowId ?? null);
      setRouteCourseId(null);
      setActiveNav("workflows");
      return;
    }

    if (page === "hot-news-theme-wizard") {
      setHotNewsModeExpanded(true);
      setHotNewsThemeWizardOpen(true);
      setHotNewsThemeWizardResumeId(
        params.themeSlug ? themeCodeFromSlug(params.themeSlug) : null,
      );
      setRouteWorkflowId(null);
      setRouteCourseId(null);
      setActiveNav("hot-news");
      return;
    }

    if (
      page === "hot-news-radar" ||
      page === "hot-news-selected" ||
      page === "hot-news-sources"
    ) {
      setHotNewsModeExpanded(true);
      const slug = params.themeSlug || "";
      const view = params.view || "radar";
      const matched = findThemeBySlug(hotNewsThemes, slug) || null;
      if (matched?.status === "enabled") {
        setSelectedHotNewsThemeId(matched.id);
        setRouteHotNewsRadar(view === "radar");
        setRouteHotNewsSelected(view === "selected");
        setRouteHotNewsSourceManagement(view === "sources");
      } else if (matched?.status === "draft") {
        // 草稿深链进工作区时改开向导
        setHotNewsThemeWizardOpen(true);
        setHotNewsThemeWizardResumeId(matched.id);
        window.history.replaceState(
          {},
          "",
          hotNewsThemePath(matched.code, "wizard"),
        );
      } else if (matched || hotNewsThemes.length > 0) {
        // 已停用或未知 slug（主题列表已就绪）：回总览，避免绑旧主题打开雷达
        if (matched?.status === "disabled") {
          showNotice("主题已停用", "请先在主题卡片上启用后再进入工作区。");
        }
        window.history.replaceState({}, "", "/modes/hot-news");
      }
      setRouteHotNewsResearchTopicId(null);
      setRouteHotNewsTopicId(null);
      setRouteHotNewsArticleId(null);
      setRouteWorkflowId(null);
      setRouteCourseId(null);
      setActiveNav("hot-news");
      return;
    }

    if (page === "hot-news-execution" && params.articleId) {
      setHotNewsModeExpanded(true);
      const articleId = params.articleId;
      setRouteHotNewsExecutionArticleId(articleId);
      setRouteHotNewsTopicId(null);
      setRouteHotNewsArticleId(null);
      setRouteHotNewsResearchTopicId(null);
      setRouteWorkflowId(null);
      setRouteCourseId(null);
      setActiveNav("hot-news");
      // 深链同步主题：优先 ?documentId= / ?job= 走 documents 读路径
      void resolveHotNewsDocumentIdFromLocation()
        .then((documentId) => getHotNewsArticle(articleId, documentId))
        .then((article) => getHotNewsTopic(article.topicId))
        .then((topic) => {
          if (topic.themeId) {
            setSelectedHotNewsThemeId(topic.themeId);
          }
        })
        .catch(() => {});
      return;
    }

    if (page === "hot-news-collect-execution") {
      setHotNewsModeExpanded(true);
      const slug = params.themeSlug || "";
      const matched = findThemeBySlug(hotNewsThemes, slug) || null;
      if (matched) {
        setSelectedHotNewsThemeId(matched.id);
        setRouteHotNewsCollectThemeId(matched.id);
      } else {
        setRouteHotNewsCollectThemeId(slug);
      }
      setRouteHotNewsRadar(false);
      setRouteHotNewsSelected(false);
      setRouteHotNewsSourceManagement(false);
      setRouteHotNewsExecutionArticleId(null);
      setRouteHotNewsResearchTopicId(null);
      setRouteHotNewsTopicId(null);
      setRouteHotNewsArticleId(null);
      setRouteWorkflowId(null);
      setRouteCourseId(null);
      setActiveNav("hot-news");
      return;
    }

    if (page === "hot-news-article" && params.topicId && params.articleId) {
      setHotNewsModeExpanded(true);
      const topicId = params.topicId;
      setRouteHotNewsTopicId(topicId);
      setRouteHotNewsArticleId(params.articleId);
      setRouteHotNewsResearchTopicId(null);
      setRouteHotNewsSourceManagement(false);
      setRouteWorkflowId(null);
      setRouteCourseId(null);
      setActiveNav("hot-news");
      void getHotNewsTopic(topicId)
        .then((topic) => {
          if (topic.themeId) {
            setSelectedHotNewsThemeId(topic.themeId);
          }
        })
        .catch(() => {});
      return;
    }

    if (page === "hot-news-research" && params.topicId) {
      setHotNewsModeExpanded(true);
      const topicId = params.topicId;
      setRouteHotNewsResearchTopicId(topicId);
      setRouteHotNewsTopicId(null);
      setRouteHotNewsArticleId(null);
      setRouteHotNewsSourceManagement(false);
      setActiveNav("hot-news");
      void getHotNewsTopic(topicId)
        .then((topic) => {
          if (topic.themeId) {
            setSelectedHotNewsThemeId(topic.themeId);
          }
        })
        .catch(() => {});
      return;
    }

    if (page === "hot-news-themes") {
      setHotNewsModeExpanded(true);
      setRouteHotNewsResearchTopicId(null);
      setRouteHotNewsSourceManagement(false);
      setRouteHotNewsRadar(false);
      setRouteHotNewsSelected(false);
      setRouteWorkflowId(null);
      setRouteCourseId(null);
      setActiveNav("hot-news");
      return;
    }

    if (page === "recycle-bin") {
      setRouteWorkflowId(null);
      setRouteCourseId(null);
      setThemeWizardOpen(false);
      setActiveNav("recycle-bin");
      return;
    }

    if (page === "course-themes") {
      setCourseModeExpanded(true);
      setThemeWizardOpen(false);
      setThemeWizardResumeId(null);
      setRouteCourseId(null);
      setRouteWorkflowId(null);
      setActiveNav("mode");
      return;
    }

    if (page === "settings") {
      setRouteCourseId(null);
      setRouteWorkflowId(null);
      setActiveNav("settings");
      setSettingsSectionHint(params.section ?? null);
      return;
    }

    if (page === "executions") {
      setRouteCourseId(null);
      setRouteWorkflowId(null);
      setActiveNav("executions");
      const jobId = params.jobId ?? null;
      setSelectedGenerationJobId(jobId);
      if (jobId) {
        void loadJob(jobId)
          .then(async (job) => {
            setGenerationJobs((current) =>
              current.some((item) => item.id === job.id)
                ? current.map((item) => (item.id === job.id ? job : item))
                : [job, ...current],
            );
            await alignExecutionThemeForJob(job);
          })
          .catch(() => undefined);
      }
      return;
    }

    setRouteCourseId(null);
    setRouteWorkflowId(null);
    setActiveNav("dashboard");
  }

  return {
    ...s,
    syncRoute
  };
}
