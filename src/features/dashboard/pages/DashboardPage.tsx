import { Activity, Layers3 } from "lucide-react";
import type { JobData } from "../../../shared/api/jobs";
import { formatHomeJobTime } from "../../../shared/format/time";
import { hotNewsThemePath } from "../../course/api";
import type { ContentThemeData } from "../../course/api";
import type { Course } from "../../course/model";
import { jobStatusLabel } from "../../executions/pages/ExecutionTrajectoryPage";
import {
  homeArticleDisplayTitle,
  homeJobTypeLabel,
  homeRecentTaskSubject,
  homeWorkflowRunLabel,
  homeWorkflowTypeLabel,
  type HomeRecentWorkflowTask,
} from "../../../app/appShellUi";

export type HomeWorkflowPulse = {
  id: string;
  key: string;
  scene: string;
  name: string;
  status: string;
  lastRunStatus: string | null;
  lastRunAt: string | null;
  lastSuccessAt: string | null;
  lastSuccessJobId: string | null;
};

export type DashboardPageProps = {
  activeJobCount: number;
  failedJobCount: number;
  aiReady: boolean;
  wechatConfigured: boolean;
  navigate: (path: string) => void | Promise<void>;
  totalArticleCount: number;
  courseArticleCount: number;
  homeHotArticleCount: number;
  enabledThemeTotal: number;
  contentThemes: ContentThemeData[];
  hotNewsEnabledCount: number;
  homeSkillCount: number;
  recentSuccessArticles: JobData[];
  courses: Course[];
  homeJobTitleByEntityId: Record<string, string>;
  openHomeWorkflowTrajectory: (options: {
    jobId: string;
    key?: string;
    scene?: string;
  }) => void | Promise<void>;
  homeWorkflows: HomeWorkflowPulse[];
  activeCourseThemeCount: number;
  courseSeriesCount: number;
  themeRoutePath: string;
  hotNewsThemes: ContentThemeData[];
  homeSelectedTopics: number;
  primaryHotNewsTheme: ContentThemeData | null;
  homeRecentTasks: HomeRecentWorkflowTask[];
};

export function DashboardPage(props: DashboardPageProps) {
  const {
    activeJobCount,
    failedJobCount,
    aiReady,
    wechatConfigured,
    navigate,
    totalArticleCount,
    courseArticleCount,
    homeHotArticleCount,
    enabledThemeTotal,
    contentThemes,
    hotNewsEnabledCount,
    homeSkillCount,
    recentSuccessArticles,
    courses,
    homeJobTitleByEntityId,
    openHomeWorkflowTrajectory,
    homeWorkflows,
    activeCourseThemeCount,
    courseSeriesCount,
    themeRoutePath,
    hotNewsThemes,
    homeSelectedTopics,
    primaryHotNewsTheme,
    homeRecentTasks,
  } = props;

  return (
                <section className="home-dashboard">
                  <header className="home-context-bar">
                    <p>
                      内容生产台总览 · 进行中 {activeJobCount}
                      {failedJobCount > 0 ? ` · 失败 ${failedJobCount}` : ""}
                    </p>
                    <div className="home-health">
                      <button
                        type="button"
                        className={aiReady ? "is-ready" : "is-warn"}
                        onClick={() => void navigate("/settings?section=ai")}
                      >
                        配置 AI · {aiReady ? "已就绪" : "未配置"}
                      </button>
                      <button
                        type="button"
                        className={wechatConfigured ? "is-ready" : "is-muted"}
                        onClick={() =>
                          void navigate("/settings?section=wechatMp")
                        }
                      >
                        公众号配置 · {wechatConfigured ? "已就绪" : "未配置"}
                      </button>
                    </div>
                  </header>

                  {!aiReady && (
                    <div className="home-alerts">
                      <button
                        type="button"
                        className="home-alert is-warn"
                        onClick={() => void navigate("/settings?section=ai")}
                      >
                        <strong>先配置文本 AI</strong>
                        <span>生成原稿、热点成文都依赖文本模型</span>
                      </button>
                    </div>
                  )}

                  <div className="home-overview-grid">
                    <div className="home-overview-card">
                      <span>已生成文章</span>
                      <strong>{totalArticleCount}</strong>
                      <small>
                        课程 {courseArticleCount} · 热点 {homeHotArticleCount}
                      </small>
                    </div>
                    <div className="home-overview-card">
                      <span>启用主题</span>
                      <strong>{enabledThemeTotal}</strong>
                      <small>
                        课程{" "}
                        {
                          contentThemes.filter((theme) => theme.status === "enabled")
                            .length
                        }{" "}
                        · 热点 {hotNewsEnabledCount}
                      </small>
                    </div>
                    <button
                      type="button"
                      className="home-overview-card is-clickable"
                      onClick={() => void navigate("/settings?section=skills")}
                    >
                      <span>Skill</span>
                      <strong>{homeSkillCount}</strong>
                      <small>点击打开 Skill 管理</small>
                    </button>
                    <button
                      type="button"
                      className="home-overview-card is-clickable"
                      onClick={() => void navigate("/executions")}
                    >
                      <span>进行中任务</span>
                      <strong>{activeJobCount}</strong>
                      <small>
                        {failedJobCount > 0
                          ? `含失败 ${failedJobCount}`
                          : "排队或执行中"}
                      </small>
                    </button>
                  </div>

                  <div className="home-split-grid">
                    <section className="home-panel">
                      <div className="home-panel-head">
                        <h3>最近生成成功</h3>
                        <button
                          type="button"
                          className="home-mode-link"
                          onClick={() => void navigate("/executions")}
                        >
                          全部
                        </button>
                      </div>
                      {recentSuccessArticles.length === 0 ? (
                        <p className="home-panel-empty">还没有生成成功的文章任务</p>
                      ) : (
                        <ul className="home-success-list">
                          {recentSuccessArticles.map((job) => (
                            <li key={job.id}>
                              <button
                                type="button"
                                onClick={() => {
                                  void openHomeWorkflowTrajectory({
                                    jobId: job.id,
                                  });
                                }}
                              >
                                <strong>
                                  {homeArticleDisplayTitle(
                                    job,
                                    courses,
                                    homeJobTitleByEntityId,
                                  )}
                                </strong>
                                <span>
                                  {homeJobTypeLabel(job.type)} ·{" "}
                                  {formatHomeJobTime(
                                    job.finishedAt || job.createdAt || "",
                                  )}
                                </span>
                              </button>
                            </li>
                          ))}
                        </ul>
                      )}
                    </section>

                    <section className="home-panel">
                      <div className="home-panel-head">
                        <h3>工作流最新状态</h3>
                        <button
                          type="button"
                          className="home-mode-link"
                          onClick={() =>
                            void navigate("/modes/course-system/workflows")
                          }
                        >
                          管理工作流
                        </button>
                      </div>
                      {homeWorkflows.length === 0 ? (
                        <p className="home-panel-empty">暂无工作流数据</p>
                      ) : (
                        <ul className="home-workflow-list">
                          {homeWorkflows.map((workflow) => {
                            const canOpenSuccess = Boolean(
                              workflow.lastSuccessJobId,
                            );
                            return (
                              <li key={workflow.id}>
                                <button
                                  type="button"
                                  className={
                                    canOpenSuccess ? undefined : "is-disabled"
                                  }
                                  disabled={!canOpenSuccess}
                                  title={
                                    canOpenSuccess
                                      ? "查看最近成功的执行轨迹"
                                      : "尚无成功执行记录"
                                  }
                                  onClick={() => {
                                    if (!workflow.lastSuccessJobId) return;
                                    void openHomeWorkflowTrajectory({
                                      jobId: workflow.lastSuccessJobId,
                                      key: workflow.key,
                                      scene: workflow.scene,
                                    });
                                  }}
                                >
                                  <span className="home-workflow-copy">
                                    <strong>{workflow.name}</strong>
                                    {workflow.lastSuccessAt ? (
                                      <em>
                                        最近成功 ·{" "}
                                        {formatHomeJobTime(workflow.lastSuccessAt)}
                                      </em>
                                    ) : (
                                      <em>尚无成功执行</em>
                                    )}
                                  </span>
                                  <span
                                    className={`home-job-status status-${
                                      workflow.lastSuccessJobId
                                        ? "succeeded"
                                        : workflow.lastRunStatus || "idle"
                                    }`}
                                  >
                                    {workflow.lastSuccessJobId
                                      ? "最近成功"
                                      : workflow.lastRunStatus
                                        ? homeWorkflowRunLabel(
                                            workflow.lastRunStatus,
                                          )
                                        : "尚无执行"}
                                  </span>
                                </button>
                              </li>
                            );
                          })}
                        </ul>
                      )}
                    </section>
                  </div>

                  <div className="home-mode-grid">
                    <article className="home-mode-card">
                      <header>
                        <span className="home-mode-icon">
                          <Layers3 size={20} />
                        </span>
                        <div>
                          <strong>课程体系</strong>
                          <p>主题路线、原稿、公众号文与审核</p>
                        </div>
                      </header>
                      <dl className="home-mode-stats">
                        <div>
                          <dt>主题</dt>
                          <dd>{activeCourseThemeCount}</dd>
                        </div>
                        <div>
                          <dt>模块</dt>
                          <dd>{courseSeriesCount}</dd>
                        </div>
                        <div>
                          <dt>课程</dt>
                          <dd>{courses.length}</dd>
                        </div>
                      </dl>
                      <footer>
                        <button
                          type="button"
                          className="primary-button"
                          onClick={() => void navigate(themeRoutePath)}
                        >
                          打开课程路线
                        </button>
                      </footer>
                    </article>

                    <article className="home-mode-card">
                      <header>
                        <span className="home-mode-icon home-mode-icon-hot">
                          <Activity size={20} />
                        </span>
                        <div>
                          <strong>热点资讯</strong>
                          <p>雷达采集、选题资料与成文推送</p>
                        </div>
                      </header>
                      <dl className="home-mode-stats">
                        <div>
                          <dt>主题</dt>
                          <dd>{hotNewsThemes.length}</dd>
                        </div>
                        <div>
                          <dt>已选题</dt>
                          <dd>{homeSelectedTopics}</dd>
                        </div>
                        <div>
                          <dt>已成文</dt>
                          <dd>{homeHotArticleCount}</dd>
                        </div>
                      </dl>
                      <footer>
                        <button
                          type="button"
                          className="primary-button"
                          onClick={() => {
                            if (primaryHotNewsTheme?.status === "enabled") {
                              void navigate(
                                hotNewsThemePath(
                                  primaryHotNewsTheme.code,
                                  "radar",
                                ),
                              );
                              return;
                            }
                            void navigate("/modes/hot-news");
                          }}
                        >
                          {primaryHotNewsTheme?.status === "enabled"
                            ? "打开热点雷达"
                            : "进入热点总览"}
                        </button>
                      </footer>
                    </article>
                  </div>

                  <section className="home-job-panel">
                    <div className="home-job-heading">
                      <div>
                        <h3>最近任务</h3>
                        <p>三个工作流最近执行 · 对应执行轨迹</p>
                      </div>
                      <button
                        type="button"
                        className="secondary-button"
                        onClick={() =>
                          void navigate("/modes/course-system/workflows")
                        }
                      >
                        全部任务
                      </button>
                    </div>
                    {homeRecentTasks.length === 0 ? (
                      <div className="home-job-empty">
                        <p>
                          还没有执行记录。可从课程生成、热点雷达采集或热点成文发起。
                        </p>
                        <div>
                          <button
                            type="button"
                            className="secondary-button"
                            onClick={() => void navigate(themeRoutePath)}
                          >
                            打开课程路线
                          </button>
                          <button
                            type="button"
                            className="secondary-button"
                            onClick={() => {
                              if (primaryHotNewsTheme?.status === "enabled") {
                                void navigate(
                                  hotNewsThemePath(
                                    primaryHotNewsTheme.code,
                                    "radar",
                                  ),
                                );
                                return;
                              }
                              void navigate("/modes/hot-news");
                            }}
                          >
                            打开热点雷达
                          </button>
                        </div>
                      </div>
                    ) : (
                      <ul className="home-job-list">
                        {homeRecentTasks.map((task) => {
                          const subject = homeRecentTaskSubject(
                            task,
                            courses,
                            homeJobTitleByEntityId,
                          );
                          const statusClass = (
                            task.job?.status ||
                            task.runStatus ||
                            ""
                          ).toLowerCase();
                          return (
                            <li key={task.jobId}>
                              <button
                                type="button"
                                className="home-job-row"
                                onClick={() => {
                                  void openHomeWorkflowTrajectory({
                                    jobId: task.jobId,
                                    key: task.workflowKey,
                                    scene: task.workflowScene,
                                  });
                                }}
                              >
                                <span className="home-job-type">
                                  {homeWorkflowTypeLabel(
                                    task.workflowKey,
                                    task.workflowScene,
                                    task.workflowName,
                                    task.job?.type,
                                  )}
                                </span>
                                <span className="home-job-step" title={subject}>
                                  {subject}
                                </span>
                                <span
                                  className={`home-job-status status-${statusClass}`}
                                >
                                  {task.job
                                    ? jobStatusLabel(task.job)
                                    : homeWorkflowRunLabel(task.runStatus)}
                                </span>
                                <span className="home-job-time">
                                  {formatHomeJobTime(
                                    task.job?.finishedAt ||
                                      task.job?.createdAt ||
                                      task.at,
                                  )}
                                </span>
                              </button>
                            </li>
                          );
                        })}
                      </ul>
                    )}
                  </section>
                </section>
  );
}
