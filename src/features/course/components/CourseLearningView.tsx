// @ts-nocheck
import {
  AlertTriangle,
  BookOpen,
  Bot,
  CheckCircle2,
  ChevronDown,
  ChevronRight,
  Clipboard,
  ClipboardCheck,
  ExternalLink,
  History,
  Image,
  MonitorCheck,
  Palette,
  Play,
  RefreshCcw,
  Search,
  Sparkles,
  Trash2,
  Undo2,
  Wand2,
  WandSparkles,
  X,
} from "lucide-react";
import type {
  ArticleData,
  ContentThemeData,
  CourseGenerationSettingsData,
  ImageCountOption,
  WordCountOption,
} from "../api";
import {
  type ArticleVersion,
  type AuditItem,
  type AuditReport,
  type Course,
  type CourseContent,
  type HumanReview,
  type HumanReviewStatus,
  type SyncedImage,
  type TabKey,
  type UnifiedContentVersion,
  emptyAuditReport,
  formatCourseStatus,
  imageCountLabels,
  imageNameFromSrc,
  localizedAuditItem,
  nativePublishingEnabled,
  normalizeObsidianEmbeds,
  preferCourseDocumentId,
  prepareMdniceMarkdown,
  resolveImageSrc,
  statusTone,
  tabs,
  wordCountLabels,
} from "../model";
import {
  TabContent,
} from "./TabContent";
import { CourseActionPanel } from "./CourseActionPanel";
import { CourseThemeSwitcher } from "./CourseThemeSwitcher";
import { CourseRouteRail } from "./CourseRouteRail";

export type CourseLearningViewProps = {
  activeTab: any,
  applyRepairResult: any,
  articleVersions: any,
  changeDetailTab: any,
  checkedRules: any,
  collapsedGroups: any,
  confirmAction: any,
  contentThemes: any,
  courseContent: any,
  courseGroups: any,
  dataThemeName: any,
  draftArticleVersions: any,
  generationSettings: any,
  generationSettingsError: any,
  generationSettingsLoading: any,
  humanReviews: any,
  loadState: any,
  navigate: any,
  openLatestCourseTrajectory: any,
  openMdniceTask: any,
  openVersionTrajectory: any,
  publishedCourses: any,
  removeCourseImage: any,
  resetCourseContent: any,
  resetHumanReview: any,
  restoreCourseVersion: any,
  routeCourseId: any,
  runCourseContentGeneration: any,
  runGeneration: any,
  runImageTask: any,
  runMockTask: any,
  selectedCourse: any,
  selectedThemeId: any,
  setGenerationSettings: any,
  setPublishingDirty: any,
  setSelectedId: any,
  setSelectedThemeId: any,
  setThemePickerOpen: any,
  setChengwenfangRefreshKey: any,
  showNotice: any,
  submitHumanReview: any,
  syncedImages: any,
  taskMessage: any,
  taskRunning: any,
  themeCoursePath: any,
  themePickerOpen: any,
  themeRoutePath: any,
  toggleCourseGroup: any,
  togglePublished: any,
  toggleRule: any,
  updateCourseContent: any,
  workspaceFallback: any
};

export function CourseLearningView({
  activeTab,
  applyRepairResult,
  articleVersions,
  changeDetailTab,
  checkedRules,
  collapsedGroups,
  confirmAction,
  contentThemes,
  courseContent,
  courseGroups,
  dataThemeName,
  draftArticleVersions,
  generationSettings,
  generationSettingsError,
  generationSettingsLoading,
  humanReviews,
  loadState,
  navigate,
  openLatestCourseTrajectory,
  openMdniceTask,
  openVersionTrajectory,
  publishedCourses,
  removeCourseImage,
  resetCourseContent,
  resetHumanReview,
  restoreCourseVersion,
  routeCourseId,
  runCourseContentGeneration,
  runGeneration,
  runImageTask,
  runMockTask,
  selectedCourse,
  selectedThemeId,
  setGenerationSettings,
  setPublishingDirty,
  setSelectedId,
  setSelectedThemeId,
  setThemePickerOpen,
  setChengwenfangRefreshKey,
  showNotice,
  submitHumanReview,
  syncedImages,
  taskMessage,
  taskRunning,
  themeCoursePath,
  themePickerOpen,
  themeRoutePath,
  toggleCourseGroup,
  togglePublished,
  toggleRule,
  updateCourseContent,
  workspaceFallback
}: CourseLearningViewProps) {
  return (
    <>
      <CourseThemeSwitcher
        contentThemes={contentThemes}
        dataThemeName={dataThemeName}
        navigate={navigate}
        selectedThemeId={selectedThemeId}
        setSelectedThemeId={setSelectedThemeId}
        setThemePickerOpen={setThemePickerOpen}
        themePickerOpen={themePickerOpen}
        workspaceFallback={workspaceFallback}
      />
      <section
        className={`content-grid ${routeCourseId ? "course-detail-route" : "course-route-list"}`}
      >
        {!routeCourseId && (
          <div className="course-panel">
            <div className="panel-header">
              <div>
                <h2>课程路线</h2>
                <p>按系列推进学习和内容生产状态</p>
              </div>
              <button
                className="ghost-button"
                disabled={loadState === "loading"}
                onClick={() =>
                  setChengwenfangRefreshKey((current) => current + 1)
                }
              >
                <RefreshCcw size={15} />
                {loadState === "loading" ? "加载中" : "刷新"}
              </button>
            </div>

            <div className="course-list">
              {loadState === "loading" && (
                <div className="route-loading">
                  <RefreshCcw size={20} />
                  <strong>正在读取完整课程路线</strong>
                  <span>同步系列、课程标题和内容状态...</span>
                </div>
              )}
              {loadState !== "loading" &&
                courseGroups.length === 0 && (
                  <div className="empty-search">
                    <Search size={18} />
                    <strong>没有匹配的课程</strong>
                    <span>请调整搜索词或切换工作区。</span>
                  </div>
                )}
              {loadState !== "loading" &&
                courseGroups.map((group, groupIndex) => {
                  const collapsed =
                    collapsedGroups[group.series] ?? true;
                  const generated = group.courses.filter(
                    (course) => course.draftWords > 0,
                  ).length;
                  return (
                    <div
                      className="course-group"
                      key={group.series}
                    >
                      <button
                        className="course-group-head"
                        onClick={() =>
                          toggleCourseGroup(group.series)
                        }
                      >
                        <span className="course-group-copy">
                          <span className="course-group-title">
                            {group.series}
                          </span>
                          <span className="course-group-details">
                            <span className="series-index">
                              {String(groupIndex + 1).padStart(
                                2,
                                "0",
                              )}
                            </span>
                            <span className="course-group-meta">
                              {group.courses.length} 课 ·{" "}
                              {generated} 篇正文 · {group.completed}{" "}
                              课已审核
                            </span>
                          </span>
                        </span>
                        <span
                          className="series-progress"
                          aria-hidden="true"
                        >
                          <span
                            style={{
                              width: `${group.courses.length ? (generated / group.courses.length) * 100 : 0}%`,
                            }}
                          />
                        </span>
                        <span className="course-group-icon">
                          {collapsed ? (
                            <ChevronRight size={18} />
                          ) : (
                            <ChevronDown size={18} />
                          )}
                        </span>
                      </button>

                      {!collapsed && (
                        <div className="course-group-body">
                          {group.courses.map((course) => (
                            <button
                              className={`course-row ${course.id === selectedCourse.id ? "selected" : ""}`}
                              key={course.id}
                              onClick={() => {
                                setSelectedId(course.id);
                                navigate(themeCoursePath(course.id));
                              }}
                            >
                              <CourseRouteRail
                                course={course}
                                groupIndex={groupIndex}
                                publishedCourses={publishedCourses}
                              />
                              <div className="course-side">
                                <span
                                  className={`status-pill ${statusTone[course.status] || "tone-muted"}`}
                                >
                                  {course.draftWords
                                    ? formatCourseStatus(course.status)
                                    : "未生成正文"}
                                </span>
                              </div>
                            </button>
                          ))}
                        </div>
                      )}
                    </div>
                  );
                })}
            </div>
          </div>
        )}

        {routeCourseId && (
          <div className="detail-panel">
            {routeCourseId && (
              <button
                className="back-to-route"
                onClick={() => navigate(themeRoutePath)}
              >
                ← 返回课程路线
              </button>
            )}
            <div className="detail-hero">
              <div>
                <div className="course-series">
                  {selectedCourse.series}
                </div>
                <h2>
                  {selectedCourse.lesson}：{selectedCourse.title}
                  <span
                    className={`status-pill detail-status ${statusTone[selectedCourse.status] || "tone-muted"}`}
                  >
                    {selectedCourse.draftWords
                      ? formatCourseStatus(selectedCourse.status)
                      : "未生成正文"}
                  </span>
                </h2>
                <p>{selectedCourse.summary}</p>
                <div className="lifecycle-controls">
                  <span
                    className={
                      selectedCourse.draftWords ? "on" : ""
                    }
                  >
                    正文
                  </span>
                  <span
                    className={
                      selectedCourse.articleWords ? "on" : ""
                    }
                  >
                    公众号
                  </span>
                  <span
                    className={
                      selectedCourse.status === "人工已审核"
                        ? "on"
                        : ""
                    }
                  >
                    审核
                  </span>
                  <button
                    className={
                      publishedCourses[selectedCourse.id]
                        ? "on"
                        : ""
                    }
                    onClick={() =>
                      togglePublished(selectedCourse.id)
                    }
                  >
                    {publishedCourses[selectedCourse.id]
                      ? "已发布"
                      : "未发布"}
                  </button>
                </div>
              </div>
              <div className="detail-actions">
                <button
                  className="primary-button unified-generation-button"
                  disabled={
                    taskRunning ||
                    generationSettingsLoading ||
                    !generationSettings
                  }
                  onClick={runCourseContentGeneration}
                >
                  <Sparkles size={15} />
                  生成课程内容
                </button>
                <button
                  className="ghost-button trajectory-button"
                  onClick={() =>
                    void openLatestCourseTrajectory(
                      selectedCourse.id,
                    )
                  }
                >
                  <History size={15} /> 查看执行记录
                </button>
                <button
                  className="ghost-button"
                  disabled={taskRunning}
                  onClick={() => runGeneration("course_draft")}
                >
                  生成原稿
                </button>
                <button
                  className="ghost-button"
                  disabled={taskRunning}
                  onClick={() => runGeneration("wechat_article")}
                >
                  生成公众号文章
                </button>
                <button
                  className="ghost-button"
                  disabled={taskRunning}
                  onClick={runImageTask}
                >
                  生成配图
                </button>
              </div>
              <div
                className="generation-options"
                aria-label="课程生成配置"
              >
                <label>
                  <span>正文字数</span>
                  <select
                    value={
                      generationSettings?.wordCountOption || ""
                    }
                    disabled={
                      generationSettingsLoading ||
                      !generationSettings
                    }
                    onChange={(event) =>
                      setGenerationSettings((current) =>
                        current
                          ? {
                              ...current,
                              wordCountOption: event.target
                                .value as WordCountOption,
                            }
                          : current,
                      )
                    }
                  >
                    <option value="" disabled>
                      请选择
                    </option>
                    {(
                      Object.keys(
                        wordCountLabels,
                      ) as WordCountOption[]
                    ).map((value) => (
                      <option key={value} value={value}>
                        {wordCountLabels[value]}
                        {generationSettings?.recommendedWordCountOption ===
                        value
                          ? "（推荐）"
                          : ""}
                      </option>
                    ))}
                  </select>
                </label>
                <label>
                  <span>正文插图</span>
                  <select
                    value={
                      generationSettings?.imageCountOption || ""
                    }
                    disabled={
                      generationSettingsLoading ||
                      !generationSettings
                    }
                    onChange={(event) =>
                      setGenerationSettings((current) =>
                        current
                          ? {
                              ...current,
                              imageCountOption: event.target
                                .value as ImageCountOption,
                            }
                          : current,
                      )
                    }
                  >
                    <option value="" disabled>
                      请选择
                    </option>
                    {(
                      Object.keys(
                        imageCountLabels,
                      ) as ImageCountOption[]
                    ).map((value) => (
                      <option key={value} value={value}>
                        {imageCountLabels[value]}
                        {generationSettings?.recommendedImageCountOption ===
                        value
                          ? "（推荐）"
                          : ""}
                      </option>
                    ))}
                  </select>
                </label>
                <p>
                  {generationSettingsLoading
                    ? "AI 正在根据课程安排生成首次推荐…"
                    : generationSettingsError ||
                      generationSettings?.recommendationReason ||
                      ""}
                </p>
              </div>
              {taskMessage && (
                <div
                  className={`detail-task-status ${taskRunning ? "running" : ""}`}
                >
                  {taskRunning && <RefreshCcw size={14} />}
                  <span>{taskMessage}</span>
                  <button
                    onClick={() =>
                      void openLatestCourseTrajectory(
                        selectedCourse.id,
                      )
                    }
                  >
                    查看轨迹
                  </button>
                </div>
              )}
            </div>

            <div className="tab-strip">
              {tabs.map((tab) => {
                const Icon = tab.icon;
                return (
                  <button
                    className={
                      activeTab === tab.key ? "tab active" : "tab"
                    }
                    key={tab.key}
                    onClick={() => void changeDetailTab(tab.key)}
                  >
                    <Icon size={15} />
                    {tab.key === "mdnice" &&
                    !nativePublishingEnabled
                      ? "mdnice"
                      : tab.label}
                  </button>
                );
              })}
            </div>

            <TabContent
              course={selectedCourse}
              content={courseContent}
              syncedImages={syncedImages}
              tab={activeTab}
              articleVersions={articleVersions}
              draftArticleVersions={draftArticleVersions}
              onChangeContent={updateCourseContent}
              onResetContent={resetCourseContent}
              onRestoreVersion={restoreCourseVersion}
              onViewTrajectory={(workflowRunId) =>
                void openVersionTrajectory(
                  selectedCourse.id,
                  workflowRunId,
                )
              }
              onRepairApplied={applyRepairResult}
              humanReview={humanReviews[selectedCourse.id]}
              onSubmitHumanReview={submitHumanReview}
              onResetHumanReview={resetHumanReview}
              onDeleteImage={removeCourseImage}
              onConfirm={confirmAction}
              nativePublishingEnabled={nativePublishingEnabled}
              onPublishingDirtyChange={setPublishingDirty}
              showNotice={showNotice}
            />
          </div>
        )}

        <CourseActionPanel
          checkedRules={checkedRules}
          loadState={loadState}
          openMdniceTask={openMdniceTask}
          runGeneration={runGeneration}
          runImageTask={runImageTask}
          runMockTask={runMockTask}
          taskMessage={taskMessage}
          taskRunning={taskRunning}
          toggleRule={toggleRule}
        />
      </section>
    </>
  );
}