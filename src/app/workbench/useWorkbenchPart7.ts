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

export function useWorkbenchPart7(s: Record<string, any>) {
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
    openMdniceTask
  } = s;
  async function runImageTask() {
    if (!courseContent.articleArticleId) {
      setTaskMessage("当前课程没有可关联的公众号文章。");
      return;
    }
    const draftPrompts = extractIllustrationPrompts(courseContent.draft);
    let articlePrompts = extractIllustrationPrompts(courseContent.article);
    if (!draftPrompts.length && !articlePrompts.length) {
      setTaskMessage(
        "当前课程没有可识别的插图提示词，请先生成包含插图提示词的课程原稿。",
      );
      return;
    }
    setTaskRunning(true);
    setTaskMessage(
      `发现 ${Math.max(draftPrompts.length, articlePrompts.length)} 张配图，准备生成。`,
    );
    let workingDraft = courseContent.draft;
    let workingArticle = courseContent.article;
    try {
      const prompts = draftPrompts.length ? draftPrompts : articlePrompts;
      for (let index = 0; index < prompts.length; index += 1) {
        const source = prompts[index];
        let articleMarker = source.marker;
        if (!workingArticle.includes(articleMarker)) {
          workingArticle = `${workingArticle.trim()}\n\n${articleMarker}`;
          articlePrompts = extractIllustrationPrompts(workingArticle);
          articleMarker = articlePrompts.at(-1)?.marker ?? articleMarker;
        }
        setTaskMessage(`正在生成第 ${index + 1}/${prompts.length} 张配图`);
        const image = await requestImageGeneration(
          courseContent.articleArticleId,
          {
            kind: index === 0 ? "cover" : "inline",
            prompt: source.prompt,
            documentId: preferCourseDocumentId(
              courseContent.articleDocumentId,
              courseContent.articleArticleId,
            ),
          },
          (event) =>
            setTaskMessage(
              `第 ${index + 1}/${prompts.length} 张：${event.message}`,
            ),
        );
        const markdownImage = `![插图 ${index + 1}](${image.publicUrl})`;
        workingDraft = workingDraft.replace(
          source.marker,
          `${source.marker}\n\n${markdownImage}`,
        );
        workingArticle = workingArticle.replace(articleMarker, markdownImage);
      }
      if (
        courseContent.draftArticleId &&
        workingDraft !== courseContent.draft
      ) {
        await saveArticleContent(
          courseContent.draftArticleId,
          workingDraft,
          "ai_generation",
          "AI 配图插入课程原稿",
          preferCourseDocumentId(
            courseContent.draftDocumentId,
            courseContent.draftArticleId,
          ),
        );
      }
      if (
        courseContent.articleArticleId &&
        workingArticle !== courseContent.article
      ) {
        await saveArticleContent(
          courseContent.articleArticleId,
          workingArticle,
          "ai_generation",
          "AI 配图插入公众号文章",
          preferCourseDocumentId(
            courseContent.articleDocumentId,
            courseContent.articleArticleId,
          ),
        );
      }
      const refreshed = await loadWorkbenchData<WorkbenchData>(
        selectedThemeId,
      );
      setData(refreshed);
      setCourseContent((current) => ({
        ...current,
        draft: workingDraft,
        article: workingArticle,
      }));
      setTaskMessage(
        `已生成 ${prompts.length} 张配图，并同步到课程原稿和公众号文章。`,
      );
      setActiveTab("images");
    } catch (error) {
      setTaskMessage(error instanceof Error ? error.message : "图片生成失败。");
    } finally {
      setTaskRunning(false);
    }
  }
  async function removeCourseImage(src: string) {
    const match = src.match(/^\/api\/images\/([^/?#]+)/);
    if (!match) {
      setTaskMessage("这不是工作台生成的图片，无法在此删除。");
      return;
    }
    if (
      !(await confirmAction(
        "删除配图",
        "删除后会同时移除课程原稿和公众号文章中的图片引用，确认删除？",
        "danger",
      ))
    )
      return;
    setTaskRunning(true);
    setTaskMessage("正在删除图片及文章引用。");
    try {
      await deleteGeneratedImage(decodeURIComponent(match[1]));
      const [refreshed, content] = await Promise.all([
        loadWorkbenchData<WorkbenchData>(selectedThemeId),
        loadCourseContent(selectedCourse.id, selectedThemeId),
      ]);
      setData(refreshed);
      setCourseContent({
        draft: content.draft,
        article: content.article,
        draftArticleId: content.draftArticleId,
        articleArticleId: content.articleArticleId,
        draftDocumentId: preferCourseDocumentId(
          content.draftDocumentId,
          content.draftArticleId,
        ),
        articleDocumentId: preferCourseDocumentId(
          content.articleDocumentId,
          content.articleArticleId,
        ),
        images: (content.images ?? []).map((image) => ({
          name: image.name,
          path: image.url || image.path,
          articleId: selectedCourse.id,
          articleTitle: selectedCourse.title,
          articlePath: "",
        })),
        loading: false,
      });
      setTaskMessage("图片及文章引用已删除。");
    } catch (error) {
      setTaskMessage(error instanceof Error ? error.message : "图片删除失败。");
    } finally {
      setTaskRunning(false);
    }
  }
  function toggleRule(rule: string) {
    // Toggle the presentation-only checklist in the action sidebar.
    setCheckedRules((current) =>
      current.includes(rule)
        ? current.filter((item) => item !== rule)
        : [...current, rule],
    );
  }
  function toggleCourseGroup(series: string) {
    // Preserve independent collapsed state for each course series.
    setCollapsedGroups((current) => ({
      ...current,
      [series]: !(current[series] ?? series !== selectedCourse.series),
    }));
  }
  async function updateCourseContent(
    kind: "draft" | "article",
    value: string,
    source = "manual_edit",
    reason = "手动编辑",
  ) {
    // Apply an optimistic edit, then restore the previous body if persistence fails.
    const articleId =
      kind === "draft"
        ? courseContent.draftArticleId
        : courseContent.articleArticleId;
    const documentId = preferCourseDocumentId(
      kind === "draft"
        ? courseContent.draftDocumentId
        : courseContent.articleDocumentId,
      articleId,
    );
    if (!articleId) throw new Error("当前内容尚未建立数据库文章记录。");
    const previous = courseContent[kind];
    setCourseContent((current) => ({ ...current, [kind]: value }));
    try {
      const saved = await saveArticleContent(
        articleId,
        value,
        source,
        reason,
        documentId,
      );
      setCourseContent((current) => ({ ...current, [kind]: saved.content }));
      setData((current) => ({
        ...current,
        courses: current.courses.map((course) =>
          course.id === selectedCourse.id
            ? {
                ...course,
                [kind === "draft" ? "draftWords" : "articleWords"]:
                  saved.wordCount,
                updatedAt: saved.updatedAt,
              }
            : course,
        ),
      }));
      if (kind === "article") {
        setArticleVersions(await loadArticleVersions(articleId, documentId));
        setHumanReviews((current) => {
          const next = { ...current };
          delete next[selectedCourse.id];
          return next;
        });
      }
    } catch (error) {
      setCourseContent((current) => ({ ...current, [kind]: previous }));
      throw error;
    }
  }
  async function resetCourseContent(kind: "draft" | "article") {
    // Reset by creating a new restore version from the immutable initial import.
    const articleId =
      kind === "draft"
        ? courseContent.draftArticleId
        : courseContent.articleArticleId;
    const documentId = preferCourseDocumentId(
      kind === "draft"
        ? courseContent.draftDocumentId
        : courseContent.articleDocumentId,
      articleId,
    );
    if (!articleId) throw new Error("当前内容尚未建立数据库文章记录。");
    const versions = await loadArticleVersions(articleId, documentId);
    const initial = versions.find(
      (version) => version.source === "initial_import",
    );
    if (!initial) throw new Error("未找到初始导入版本。");
    const restored = await restoreArticleVersion(
      articleId,
      initial.id,
      documentId,
    );
    setCourseContent((current) => ({ ...current, [kind]: restored.content }));
    if (kind === "article") {
      setArticleVersions(await loadArticleVersions(articleId, documentId));
      setHumanReviews((current) => {
        const next = { ...current };
        delete next[selectedCourse.id];
        return next;
      });
    }
  }
  async function restoreCourseVersion(versionId: string) {
    // Restoring never mutates history; the backend creates a new current version.
    if (!courseContent.articleArticleId)
      throw new Error("当前公众号文章不存在。");
    const documentId = preferCourseDocumentId(
      courseContent.articleDocumentId,
      courseContent.articleArticleId,
    );
    const restored = await restoreArticleVersion(
      courseContent.articleArticleId,
      versionId,
      documentId,
    );
    setCourseContent((current) => ({ ...current, article: restored.content }));
    setArticleVersions(
      await loadArticleVersions(courseContent.articleArticleId, documentId),
    );
    setHumanReviews((current) => {
      const next = { ...current };
      delete next[selectedCourse.id];
      return next;
    });
  }

  return {
    ...s,
    runImageTask,
    removeCourseImage,
    toggleRule,
    toggleCourseGroup,
    updateCourseContent,
    resetCourseContent,
    restoreCourseVersion
  };
}
