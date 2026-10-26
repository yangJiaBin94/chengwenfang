import { useEffect, useMemo, useRef, useState } from "react";
import type { Dispatch, SetStateAction } from "react";
import ReactMarkdown from "react-markdown";
import remarkGfm from "remark-gfm";
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
  deleteContentTheme,
  patchContentTheme,
  repairArticle,
  repairArticleMetadata,
  resumeArticleRepair,
  runArticleAudit,
  themePathSlug,
} from "../api";
import { PublishingWorkspace } from "../../publishing/components/PublishingWorkspace";
import { ThemeWizard } from "../components/ThemeWizard";
import { formatExecutionTime } from "../../executions/pages/ExecutionTrajectoryPage";
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
  ActionButton,
} from "../components/courseEditorUi";
import {
  TabContent,
} from "../components/TabContent";
import { CourseLearningView } from "../components/CourseLearningView";


/** Course group row used by the learning route list. */
export type CourseGroup = {
  series: string;
  completed: number;
  courses: Course[];
};

/** Props for the course-system mode shell. */
export type CourseSystemShellProps = {
  activeNav: string;
  themeWizardOpen: boolean;
  themeWizardResumeId: string | null;
  setThemeWizardOpen: (open: boolean) => void;
  setThemeWizardResumeId: (id: string | null) => void;
  showNotice: (title: string, message: string) => void;
  navigate: (path: string) => void;
  confirmAction: (
    title: string,
    message: string,
    tone?: "default" | "danger",
  ) => Promise<boolean>;
  selectedThemeId: string | null;
  setSelectedThemeId: (id: string) => void;
  contentThemes: ContentThemeData[];
  setContentThemes: Dispatch<SetStateAction<ContentThemeData[]>>;
  setDeletedThemes: Dispatch<SetStateAction<ContentThemeData[]>>;
  themeDeletedNotice: string;
  setWorkbenchRefreshKey: Dispatch<SetStateAction<number>>;
  workspaceFallback: {
    themeId: string;
    modeId: string;
    themeCode: string;
    themeName: string;
  };
  courseGroups: CourseGroup[];
  metricsFallback: { courseCount: number; reviewedCount: number };
  themePickerOpen: boolean;
  setThemePickerOpen: Dispatch<SetStateAction<boolean>>;
  dataThemeName: string;
  routeCourseId: string | null;
  loadState: string;
  collapsedGroups: Record<string, boolean>;
  toggleCourseGroup: (series: string) => void;
  selectedCourse: Course;
  setSelectedId: (id: string) => void;
  themeCoursePath: (courseId: string) => string;
  themeRoutePath: string;
  publishedCourses: Record<string, boolean>;
  togglePublished: (courseId: string) => void;
  taskRunning: boolean;
  taskMessage: string;
  generationSettingsLoading: boolean;
  generationSettings: CourseGenerationSettingsData | null;
  generationSettingsError: string;
  setGenerationSettings: Dispatch<
    SetStateAction<CourseGenerationSettingsData | null>
  >;
  runCourseContentGeneration: () => void;
  openLatestCourseTrajectory: (courseId: string) => void | Promise<void>;
  runGeneration: (kind: "course_draft" | "wechat_article") => void;
  runImageTask: () => void;
  activeTab: TabKey;
  changeDetailTab: (tab: TabKey) => void | Promise<void>;
  courseContent: CourseContent;
  syncedImages: SyncedImage[];
  articleVersions: ArticleVersion[];
  draftArticleVersions: ArticleVersion[];
  updateCourseContent: (
    kind: "draft" | "article",
    value: string,
    source?: string,
    reason?: string,
  ) => Promise<void>;
  resetCourseContent: (kind: "draft" | "article") => Promise<void>;
  restoreCourseVersion: (versionId: string) => Promise<void>;
  openVersionTrajectory: (
    courseId: string,
    workflowRunId: string,
  ) => void | Promise<void>;
  applyRepairResult: (article: ArticleData) => Promise<void>;
  humanReviews: Record<string, HumanReview | undefined>;
  submitHumanReview: (
    status: Exclude<HumanReviewStatus, "pending">,
    comment: string,
  ) => void;
  resetHumanReview: () => void;
  removeCourseImage: (src: string) => Promise<void>;
  setPublishingDirty: (dirty: boolean) => void;
  openMdniceTask: () => void;
  runMockTask: () => void;
  checkedRules: string[];
  toggleRule: (key: string) => void;
};

/**
 * Course-system mode shell: ThemeWizard, theme overview, and learning route/detail.
 */
export function CourseSystemShell({
  activeNav,
  themeWizardOpen,
  themeWizardResumeId,
  setThemeWizardOpen,
  setThemeWizardResumeId,
  showNotice,
  navigate,
  confirmAction,
  selectedThemeId,
  setSelectedThemeId,
  contentThemes,
  setContentThemes,
  setDeletedThemes,
  themeDeletedNotice,
  setWorkbenchRefreshKey,
  workspaceFallback,
  courseGroups,
  metricsFallback,
  themePickerOpen,
  setThemePickerOpen,
  dataThemeName,
  routeCourseId,
  loadState,
  collapsedGroups,
  toggleCourseGroup,
  selectedCourse,
  setSelectedId,
  themeCoursePath,
  themeRoutePath,
  publishedCourses,
  togglePublished,
  taskRunning,
  taskMessage,
  generationSettingsLoading,
  generationSettings,
  generationSettingsError,
  setGenerationSettings,
  runCourseContentGeneration,
  openLatestCourseTrajectory,
  runGeneration,
  runImageTask,
  activeTab,
  changeDetailTab,
  courseContent,
  syncedImages,
  articleVersions,
  draftArticleVersions,
  updateCourseContent,
  resetCourseContent,
  restoreCourseVersion,
  openVersionTrajectory,
  applyRepairResult,
  humanReviews,
  submitHumanReview,
  resetHumanReview,
  removeCourseImage,
  setPublishingDirty,
  openMdniceTask,
  runMockTask,
  checkedRules,
  toggleRule,
}: CourseSystemShellProps) {
  return (
    <>
              {activeNav === "mode" && themeWizardOpen && (
                <ThemeWizard
                  modeId="course_system"
                  themeId={themeWizardResumeId}
                  showNotice={showNotice}
                  onCancel={() => {
                    setThemeWizardOpen(false);
                    setThemeWizardResumeId(null);
                    void navigate("/modes/course-system");
                  }}
                  onCompleted={(theme) => {
                    setThemeWizardOpen(false);
                    setThemeWizardResumeId(null);
                    setSelectedThemeId(theme.id);
                    setContentThemes((current) => {
                      const others = current.filter((item) => item.id !== theme.id);
                      return [...others, theme];
                    });
                    void navigate(
                      `/modes/course-system/themes/${themePathSlug(theme.code)}/route`,
                    );
                    setWorkbenchRefreshKey((key) => key + 1);
                  }}
                />
              )}
              {activeNav === "mode" && !themeWizardOpen && (
                <section className="mode-overview">
                  <div className="context-line">课程体系教学模式</div>
                  <header>
                    <span className="section-kicker">Teaching Mode</span>
                    <h2>课程体系教学模式</h2>
                    <p>围绕主题规划、生成和维护系列课程内容。</p>
                  </header>
                  <div className="theme-section">
                    {themeDeletedNotice && (
                      <div className="theme-deleted-notice">{themeDeletedNotice}</div>
                    )}
                    <div className="theme-section-head">
                      <h3>主题</h3>
                      <div className="theme-section-actions">
                        <span>{contentThemes.length || 1} 个主题</span>
                        <button
                          type="button"
                          className="primary-button"
                          onClick={() => {
                            setThemeWizardResumeId(null);
                            setThemeWizardOpen(true);
                            void navigate("/modes/course-system/themes/new");
                          }}
                        >
                          新建主题
                        </button>
                      </div>
                    </div>
                    <div className="theme-card-grid">
                      {contentThemes.length === 0 && (
                        <p className="theme-card-empty">
                          暂无主题。请先执行初始化种子，或点击「新建主题」。
                        </p>
                      )}
                      {contentThemes.map((theme) => (
                        <div className="theme-card-wrap" key={theme.id}>
                          <button
                            className="theme-card"
                            onClick={() => {
                              if (
                                theme.status === "draft" ||
                                theme.status === "ready_for_review"
                              ) {
                                setThemeWizardResumeId(theme.id);
                                setThemeWizardOpen(true);
                                void navigate(
                                  `/modes/course-system/themes/${themePathSlug(theme.code)}/wizard`,
                                );
                                return;
                              }
                              setSelectedThemeId(theme.id);
                              navigate(
                                `/modes/course-system/themes/${themePathSlug(theme.code)}/route`,
                              );
                            }}
                          >
                            <span className="theme-card-icon">
                              <BookOpen size={22} />
                            </span>
                            <span className="theme-card-copy">
                              <strong>{theme.name}</strong>
                              <small>
                                {theme.description || theme.status}
                              </small>
                            </span>
                            <span className="theme-card-stats">
                              <span>
                                <b>{theme.seriesCount}</b>
                                <small>课程系列</small>
                              </span>
                              <span>
                                <b>{theme.courseCount}</b>
                                <small>课程</small>
                              </span>
                            </span>
                          </button>
                          <div className="theme-card-menu">
                            {(theme.status === "enabled" ||
                              theme.status === "disabled") && (
                              <button
                                type="button"
                                onClick={() => {
                                  const nextStatus =
                                    theme.status === "enabled"
                                      ? "disabled"
                                      : "enabled";
                                  void patchContentTheme(theme.id, {
                                    status: nextStatus,
                                  })
                                    .then((updated) => {
                                      setContentThemes((current) =>
                                        current.map((item) =>
                                          item.id === updated.id ? updated : item,
                                        ),
                                      );
                                      showNotice(
                                        nextStatus === "enabled" ? "已启用" : "已停用",
                                        theme.name,
                                      );
                                    })
                                    .catch((error: unknown) =>
                                      showNotice(
                                        nextStatus === "enabled"
                                          ? "启用失败"
                                          : "停用失败",
                                        error instanceof Error
                                          ? error.message
                                          : "无法更新主题状态",
                                      ),
                                    );
                                }}
                              >
                                {theme.status === "enabled" ? "停用" : "启用"}
                              </button>
                            )}
                            <button
                              type="button"
                              onClick={() => {
                                void (async () => {
                                  const ok = await confirmAction(
                                    "删除主题？",
                                    "删除后主题进入回收站，课程路线与文章不再展示，数据保留，可随时恢复。",
                                    "danger",
                                  );
                                  if (!ok) return;
                                  try {
                                    await deleteContentTheme(theme.id);
                                    const remaining = contentThemes.filter(
                                      (item) => item.id !== theme.id,
                                    );
                                    setContentThemes(remaining);
                                    setDeletedThemes((current) => [
                                      ...current.filter((item) => item.id !== theme.id),
                                      {
                                        ...theme,
                                        status: "disabled",
                                        deletedAt: new Date().toISOString(),
                                      },
                                    ]);
                                    showNotice("已移入回收站", theme.name);
                                    if (selectedThemeId === theme.id) {
                                      const nextTheme = remaining[0];
                                      if (nextTheme) {
                                        setSelectedThemeId(nextTheme.id);
                                        void navigate(
                                          `/modes/course-system/themes/${themePathSlug(nextTheme.code)}/route`,
                                        );
                                      } else {
                                        void navigate("/modes/course-system");
                                      }
                                    }
                                  } catch (error) {
                                    showNotice(
                                      "删除失败",
                                      error instanceof Error
                                        ? error.message
                                        : "无法删除主题",
                                    );
                                  }
                                })();
                              }}
                            >
                              删除
                            </button>
                          </div>
                        </div>
                      ))}
                    </div>
                  </div>
                </section>
              )}
              {activeNav === "learning" && (
              <CourseLearningView
                activeTab={activeTab}
                applyRepairResult={applyRepairResult}
                articleVersions={articleVersions}
                changeDetailTab={changeDetailTab}
                checkedRules={checkedRules}
                collapsedGroups={collapsedGroups}
                confirmAction={confirmAction}
                contentThemes={contentThemes}
                courseContent={courseContent}
                courseGroups={courseGroups}
                dataThemeName={dataThemeName}
                draftArticleVersions={draftArticleVersions}
                generationSettings={generationSettings}
                generationSettingsError={generationSettingsError}
                generationSettingsLoading={generationSettingsLoading}
                humanReviews={humanReviews}
                loadState={loadState}
                navigate={navigate}
                openLatestCourseTrajectory={openLatestCourseTrajectory}
                openMdniceTask={openMdniceTask}
                openVersionTrajectory={openVersionTrajectory}
                publishedCourses={publishedCourses}
                removeCourseImage={removeCourseImage}
                resetCourseContent={resetCourseContent}
                resetHumanReview={resetHumanReview}
                restoreCourseVersion={restoreCourseVersion}
                routeCourseId={routeCourseId}
                runCourseContentGeneration={runCourseContentGeneration}
                runGeneration={runGeneration}
                runImageTask={runImageTask}
                runMockTask={runMockTask}
                selectedCourse={selectedCourse}
                selectedThemeId={selectedThemeId}
                setGenerationSettings={setGenerationSettings}
                setPublishingDirty={setPublishingDirty}
                setSelectedId={setSelectedId}
                setSelectedThemeId={setSelectedThemeId}
                setThemePickerOpen={setThemePickerOpen}
                setWorkbenchRefreshKey={setWorkbenchRefreshKey}
                showNotice={showNotice}
                submitHumanReview={submitHumanReview}
                syncedImages={syncedImages}
                taskMessage={taskMessage}
                taskRunning={taskRunning}
                themeCoursePath={themeCoursePath}
                themePickerOpen={themePickerOpen}
                themeRoutePath={themeRoutePath}
                toggleCourseGroup={toggleCourseGroup}
                togglePublished={togglePublished}
                toggleRule={toggleRule}
                updateCourseContent={updateCourseContent}
                workspaceFallback={workspaceFallback}
              />
              )}
    </>
  );
}

