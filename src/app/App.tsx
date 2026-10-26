import { Search } from "lucide-react";
import { WorkflowPage } from "../features/workflow/pages/WorkflowPage";
import { NotificationCenter } from "../features/notifications/pages/NotificationCenter";
import { ExecutionTrajectoryPage } from "../features/executions/pages/ExecutionTrajectoryPage";
import { AiSettingsPage } from "../features/settings/pages/SettingsPage";
import { CourseSystemShell } from "../features/course/pages/CourseSystemShell";
import { HotNewsModeShell } from "../features/hotnews/pages/HotNewsModeShell";
import { AppDialog } from "./appShellUi";
import { DashboardPage } from "../features/dashboard/pages/DashboardPage";
import { RecycleBinPage } from "../features/recycle-bin/pages/RecycleBinPage";
import { AppSidebar } from "./AppSidebar";
import { useChengwenfangApp } from "./chengwenfang/useChengwenfangApp";
import { retryFailedJob } from "../shared/api/jobs";

export function App() {
  const bag = useChengwenfangApp();
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
    restoreCourseVersion,
    applyRepairResult,
    saveAiSettings,
    submitHumanReview,
    resetHumanReview
  } = bag;

  return (
    <>
      <main className="app-shell">
        <AppSidebar
          activeNav={activeNav}
          courseModeExpanded={courseModeExpanded}
          setCourseModeExpanded={setCourseModeExpanded}
          hotNewsModeExpanded={hotNewsModeExpanded}
          setHotNewsModeExpanded={setHotNewsModeExpanded}
          navigate={navigate}
          themeRoutePath={themeRoutePath}
          routeHotNewsRadar={routeHotNewsRadar}
          routeHotNewsSelected={routeHotNewsSelected}
          routeHotNewsSourceManagement={routeHotNewsSourceManagement}
          routeHotNewsTopicId={routeHotNewsTopicId}
          routeHotNewsArticleId={routeHotNewsArticleId}
          routeHotNewsResearchTopicId={routeHotNewsResearchTopicId}
          routeHotNewsExecutionArticleId={routeHotNewsExecutionArticleId}
          routeHotNewsCollectThemeId={routeHotNewsCollectThemeId}
          hotNewsThemeWizardOpen={hotNewsThemeWizardOpen}
          setHotNewsThemePickerOpen={setHotNewsThemePickerOpen}
          selectedHotNewsThemeMeta={selectedHotNewsThemeMeta}
        />
        <section className="workspace">
          <header className="topbar">
            <div>
              <div className="eyebrow">
                {loadState === "ready" ? "Content Studio" : "Chengwenfang Demo"}
              </div>
              <h1>
                {activeNav === "dashboard" || activeNav === "settings"
                  ? "内容生产台"
                  : activeNav === "hot-news"
                    ? "热点资讯模式"
                    : activeNav === "executions"
                      ? "课程内容执行轨迹"
                      : activeNav === "workflows"
                        ? "工作流"
                        : activeNav === "recycle-bin"
                          ? "回收站"
                          : data.workspace.modeName}
              </h1>
            </div>
            <div className="topbar-actions">
              {activeNav === "learning" && !routeCourseId && (
                <div className="search-box">
                  <Search size={17} />
                  <input
                    aria-label="搜索课程、素材、文章状态"
                    placeholder="搜索课程、素材、文章状态"
                    value={searchQuery}
                    onChange={(event) => setSearchQuery(event.target.value)}
                  />
                </div>
              )}
              <NotificationCenter
                onNavigate={navigate}
                confirmRetry={(title, message) => confirmAction(title, message)}
                onRetry={async (jobId) => {
                  const job = await retryFailedJob(jobId);
                  if (job.type === "hot_news_article_generation") {
                    return;
                  }
                  setSelectedGenerationJobId(job.id);
                  await refreshGenerationJobs(job.id);
                  navigate(
                    `/executions?job=${encodeURIComponent(job.id)}&course=${encodeURIComponent(job.entityId)}`,
                  );
                }}
              />
            </div>
          </header>

          {activeNav === "hot-news" ? (
            <HotNewsModeShell
              hotNewsThemeWizardOpen={hotNewsThemeWizardOpen}
              hotNewsThemeWizardResumeId={hotNewsThemeWizardResumeId}
              setHotNewsThemeWizardOpen={setHotNewsThemeWizardOpen}
              setHotNewsThemeWizardResumeId={setHotNewsThemeWizardResumeId}
              showNotice={showNotice}
              navigate={navigate}
              confirmAction={confirmAction}
              promptAction={promptAction}
              setHotNewsThemes={setHotNewsThemes}
              setSelectedHotNewsThemeId={setSelectedHotNewsThemeId}
              refreshCourseSystemThemes={refreshCourseSystemThemes}
              routeHotNewsRadar={routeHotNewsRadar}
              routeHotNewsSelected={routeHotNewsSelected}
              routeHotNewsSourceManagement={routeHotNewsSourceManagement}
              selectedHotNewsThemeMeta={selectedHotNewsThemeMeta}
              selectedHotNewsThemeId={selectedHotNewsThemeId}
              hotNewsThemePickerOpen={hotNewsThemePickerOpen}
              setHotNewsThemePickerOpen={setHotNewsThemePickerOpen}
              hotNewsThemes={hotNewsThemes}
              hotNewsDefaultSearchQueries={hotNewsDefaultSearchQueries}
              hotNewsDefaultGithubQuery={hotNewsDefaultGithubQuery}
              hotNewsThemeSlug={hotNewsThemeSlug}
              routeHotNewsCollectThemeId={routeHotNewsCollectThemeId}
              collectHotNewsThemeSlug={collectHotNewsThemeSlug}
              collectHotNewsThemeMeta={collectHotNewsThemeMeta}
              routeHotNewsExecutionArticleId={routeHotNewsExecutionArticleId}
              routeHotNewsArticleId={routeHotNewsArticleId}
              routeHotNewsResearchTopicId={routeHotNewsResearchTopicId}
            />
          ) : activeNav === "settings" ? (
            <AiSettingsPage
              settings={aiSettings}
              updatedAt={aiSettingsUpdatedAt}
              onSave={saveAiSettings}
              initialSection={settingsSectionHint}
            />
          ) : activeNav === "workflows" ? (
            <WorkflowPage
              requestedId={routeWorkflowId}
              courses={courses.map((course: { id: string; title: string; series: string; lesson: string }) => ({
                id: course.id,
                title: course.title,
                series: course.series,
                lesson: course.lesson,
              }))}
              onOpenWorkflow={(workflowId) =>
                navigate(
                  workflowId
                    ? `/modes/course-system/workflows/${encodeURIComponent(workflowId)}`
                    : "/modes/course-system/workflows",
                )
              }
              onOpenExecution={(jobId) =>
                navigate(`/executions?job=${encodeURIComponent(jobId)}`)
              }
              showNotice={showNotice}
              confirmAction={confirmAction}
            />
          ) : activeNav === "recycle-bin" ? (
            <RecycleBinPage
              deletedThemes={deletedThemes}
              themeDeletedNotice={themeDeletedNotice}
              setDeletedThemes={setDeletedThemes}
              setHotNewsThemes={setHotNewsThemes}
              setContentThemes={setContentThemes}
              setThemeDeletedNotice={setThemeDeletedNotice}
              showNotice={showNotice}
            />
          ) : activeNav === "executions" ? (
            <ExecutionTrajectoryPage
              jobs={generationJobs}
              events={generationEvents}
              nodes={generationNodes}
              versions={generationVersions}
              courses={courses}
              themes={
                contentThemes.length
                  ? contentThemes.filter((theme: { status: string }) => theme.status !== "disabled")
                  : selectedThemeMeta
                    ? [selectedThemeMeta]
                    : []
              }
              selectedThemeId={selectedThemeId}
              themePickerOpen={executionThemePickerOpen}
              onThemePickerOpenChange={setExecutionThemePickerOpen}
              onThemeChange={(themeId) => {
                setExecutionThemePickerOpen(false);
                setSelectedThemeId(themeId);
                setSelectedGenerationJobId(null);
                void navigate("/executions");
              }}
              selectedJobId={selectedGenerationJobId}
              onSelect={selectGenerationJob}
              onBackToCourse={(courseId) =>
                navigate(themeCoursePath(courseId))
              }
              onRetry={(jobId) => {
                void retryFailedJob(jobId)
                  .then((job) => {
                    setSelectedGenerationJobId(job.id);
                    void refreshGenerationJobs(job.id);
                  })
                  .catch((error: unknown) => {
                    showNotice(
                      "重试失败",
                      error instanceof Error
                        ? error.message
                        : "失败步骤重试失败",
                    );
                  });
              }}
            />
          ) : (
            <>
              {(activeNav === "mode" || activeNav === "learning") && (
                <CourseSystemShell
                  activeNav={activeNav}
                  themeWizardOpen={themeWizardOpen}
                  themeWizardResumeId={themeWizardResumeId}
                  setThemeWizardOpen={setThemeWizardOpen}
                  setThemeWizardResumeId={setThemeWizardResumeId}
                  showNotice={showNotice}
                  navigate={navigate}
                  confirmAction={confirmAction}
                  selectedThemeId={selectedThemeId}
                  setSelectedThemeId={setSelectedThemeId}
                  contentThemes={contentThemes}
                  setContentThemes={setContentThemes}
                  setDeletedThemes={setDeletedThemes}
                  themeDeletedNotice={themeDeletedNotice}
                  setChengwenfangRefreshKey={setChengwenfangRefreshKey}
                  workspaceFallback={{
                    themeId: data.workspace.themeId,
                    modeId: data.workspace.modeId,
                    themeCode: data.workspace.themeCode,
                    themeName: data.workspace.themeName,
                  }}
                  courseGroups={courseGroups}
                  metricsFallback={{
                    courseCount: data.metrics.courseCount,
                    reviewedCount: data.metrics.reviewedCount,
                  }}
                  themePickerOpen={themePickerOpen}
                  setThemePickerOpen={setThemePickerOpen}
                  dataThemeName={data.workspace.themeName}
                  routeCourseId={routeCourseId}
                  loadState={loadState}
                  collapsedGroups={collapsedGroups}
                  toggleCourseGroup={toggleCourseGroup}
                  selectedCourse={selectedCourse}
                  setSelectedId={setSelectedId}
                  themeCoursePath={themeCoursePath}
                  themeRoutePath={themeRoutePath}
                  publishedCourses={publishedCourses}
                  togglePublished={togglePublished}
                  taskRunning={taskRunning}
                  taskMessage={taskMessage}
                  generationSettingsLoading={generationSettingsLoading}
                  generationSettings={generationSettings}
                  generationSettingsError={generationSettingsError}
                  setGenerationSettings={setGenerationSettings}
                  runCourseContentGeneration={runCourseContentGeneration}
                  openLatestCourseTrajectory={openLatestCourseTrajectory}
                  runGeneration={runGeneration}
                  runImageTask={runImageTask}
                  activeTab={activeTab}
                  changeDetailTab={changeDetailTab}
                  courseContent={courseContent}
                  syncedImages={syncedImages}
                  articleVersions={articleVersions}
                  draftArticleVersions={draftArticleVersions}
                  updateCourseContent={updateCourseContent}
                  resetCourseContent={resetCourseContent}
                  restoreCourseVersion={restoreCourseVersion}
                  openVersionTrajectory={openVersionTrajectory}
                  applyRepairResult={applyRepairResult}
                  humanReviews={humanReviews}
                  submitHumanReview={submitHumanReview}
                  resetHumanReview={resetHumanReview}
                  removeCourseImage={removeCourseImage}
                  setPublishingDirty={setPublishingDirty}
                  openMdniceTask={openMdniceTask}
                  runMockTask={runMockTask}
                  checkedRules={checkedRules}
                  toggleRule={toggleRule}
                />
              )}
              {activeNav === "dashboard" && (
                <DashboardPage
                  activeJobCount={activeJobCount}
                  failedJobCount={failedJobCount}
                  aiReady={aiReady}
                  wechatConfigured={wechatConfigured}
                  navigate={navigate}
                  totalArticleCount={totalArticleCount}
                  courseArticleCount={courseArticleCount}
                  homeHotArticleCount={homeHotArticleCount}
                  enabledThemeTotal={enabledThemeTotal}
                  contentThemes={contentThemes}
                  hotNewsEnabledCount={hotNewsEnabledCount}
                  homeSkillCount={homeSkillCount}
                  recentSuccessArticles={recentSuccessArticles}
                  courses={courses}
                  homeJobTitleByEntityId={homeJobTitleByEntityId}
                  openHomeWorkflowTrajectory={openHomeWorkflowTrajectory}
                  homeWorkflows={homeWorkflows}
                  activeCourseThemeCount={activeCourseThemeCount}
                  courseSeriesCount={courseSeriesCount}
                  themeRoutePath={themeRoutePath}
                  hotNewsThemes={hotNewsThemes}
                  homeSelectedTopics={homeSelectedTopics}
                  primaryHotNewsTheme={primaryHotNewsTheme}
                  homeRecentTasks={homeRecentTasks}
                />
              )}
            </>
          )}
        </section>
      </main>
      {appDialog && <AppDialog dialog={appDialog} onClose={closeAppDialog} />}
    </>
  );
}


