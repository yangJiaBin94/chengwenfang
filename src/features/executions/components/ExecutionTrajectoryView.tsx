// @ts-nocheck
import { useEffect, useMemo, useState } from "react";
import {
  AlertTriangle,
  ArrowLeft,
  BookOpen,
  CheckCircle2,
  ChevronDown,
  ChevronRight,
  ClipboardCheck,
  History,
  RefreshCcw,
} from "lucide-react";
import type {
  ArticleVersionData,
  ContentThemeData,
} from "../../course/api";
import type {
  JobData,
  JobEventData,
  WorkflowNodeData,
} from "../../../shared/api/jobs";
import { ExecutionAttemptSwitcher } from "../../../shared/components/ExecutionAttemptSwitcher";
import { formatExecutionTime } from "../../../shared/format/time";
import {
  isExecutionFailureEvent,
  generationStepIndex,
  completedExecutionMessage,
  formatGenerationSnapshot,
  workflowNodeTitle,
  eventStepTitle,
  technicalEventMessage,
  workflowNodeParentKey,
  buildExecutionAttemptBatches,
  WorkflowNodeRow,
  displayJobStatus,
  jobStatusLabel,
  uniqueGenerationPipeline,
  Course,
} from "./executionHelpers";

/** 由页面拆出的视图。 */
export function ExecutionTrajectoryView(props: Record<string, any>) {
  const {
    HISTORY_PAGE_SIZE,
    activeIndex,
    attemptBatches,
    attemptEvents,
    attemptNodes,
    course,
    courses,
    events,
    failedStepTitle,
    generationReport,
    groupedNodes,
    historyPageCount,
    isExecuting,
    isRetrying,
    latestEvent,
    mappedEvents,
    onBackToCourse,
    onRetry,
    onSelect,
    onThemeChange,
    onThemePickerOpenChange,
    pagedJobs,
    safeHistoryPage,
    selected,
    selectedAttempt,
    selectedJobId,
    selectedStatusText,
    selectedTheme,
    selectedThemeId,
    setHistoryPage,
    setSelectedAttemptNumber,
    setShowEvents,
    setShowNodeDetails,
    setShowSkills,
    showEvents,
    showNodeDetails,
    showSkills,
    skills,
    themePickerOpen,
    themes,
    versions,
    visibleJobs,
  } = props;
  return (
    <section className="execution-page">
      <header className="execution-heading">
        <div>
          <span className="section-kicker">Execution Trace</span>
          <h2>课程内容执行轨迹</h2>
          <p>
            按主题查看 AI 生成线路、节点结果、Skill 快照和文章版本
            {selectedTheme ? ` · 当前 ${selectedTheme.name}` : ""}。
          </p>
        </div>
        <div className="execution-heading-actions">
          {themes.length > 0 ? (
            <div className="theme-switcher execution-theme-switcher">
              <button
                className="theme-switcher-button"
                type="button"
                aria-expanded={themePickerOpen}
                onClick={() => onThemePickerOpenChange(!themePickerOpen)}
              >
                <span>{selectedTheme?.name || "选择主题"}</span>
                <ChevronDown size={14} />
              </button>
              {themePickerOpen ? (
                <div className="theme-switcher-menu">
                  {themes.map((theme) => (
                    <button
                      className={
                        theme.id === selectedThemeId ? "selected" : ""
                      }
                      key={theme.id}
                      type="button"
                      onClick={() => onThemeChange(theme.id)}
                    >
                      <span>{theme.name}</span>
                      {theme.id === selectedThemeId ? (
                        <CheckCircle2 size={15} />
                      ) : null}
                    </button>
                  ))}
                </div>
              ) : null}
            </div>
          ) : null}
          {selected?.entityId && course ? (
            <button
              className="ghost-button"
              onClick={() => onBackToCourse(selected.entityId)}
            >
              <ArrowLeft size={15} /> 返回课程详情
            </button>
          ) : null}
        </div>
      </header>

      <div className="execution-layout">
        <aside className="execution-history">
          <div className="execution-panel-title">
            <History size={16} />
            生成记录
            {selectedTheme ? (
              <em className="execution-theme-tag">{selectedTheme.name}</em>
            ) : null}
          </div>
          <div className="execution-history-list">
            {pagedJobs.length ? (
              pagedJobs.map((job) => {
                const itemCourse = courses.find(
                  (item) => item.id === job.entityId,
                );
                const statusText = jobStatusLabel(
                  job.id === selected?.id ? selected : job,
                );
                return (
                  <button
                    className={`execution-record ${job.id === selected?.id ? "active" : ""}`}
                    key={job.id}
                    onClick={() => onSelect(job.id)}
                  >
                    <span className={`execution-state state-${job.status}`}>
                      {statusText}
                    </span>
                    <strong>
                      {itemCourse
                        ? `${itemCourse.lesson}：${itemCourse.title}`
                        : job.entityId}
                    </strong>
                    <small>{formatExecutionTime(job.createdAt)}</small>
                  </button>
                );
              })
            ) : (
              <div className="execution-empty">
                {selectedTheme
                  ? `「${selectedTheme.name}」暂无生成记录`
                  : "暂无生成记录"}
              </div>
            )}
          </div>
          {visibleJobs.length > HISTORY_PAGE_SIZE ? (
            <div className="execution-history-pager">
              <button
                type="button"
                disabled={safeHistoryPage <= 1}
                onClick={() => setHistoryPage((page) => Math.max(1, page - 1))}
              >
                上一页
              </button>
              <span>
                {safeHistoryPage} / {historyPageCount}
              </span>
              <button
                type="button"
                disabled={safeHistoryPage >= historyPageCount}
                onClick={() =>
                  setHistoryPage((page) => Math.min(historyPageCount, page + 1))
                }
              >
                下一页
              </button>
            </div>
          ) : null}
        </aside>

        <div className="execution-detail">
          {selected ? (
            <>
              <div className="execution-summary">
                <div>
                  <span>
                    {selectedTheme?.name || "主题"} ·{" "}
                    {course?.series || "课程生成"} · 生成记录
                  </span>
                  <h3>
                    {course
                      ? `${course.lesson}：${course.title}`
                      : selected.entityId}
                  </h3>
                </div>
                <span className={`execution-state state-${selected.status}`}>
                  {selectedStatusText}
                </span>
              </div>

              {generationReport && (
                <section className="execution-subsection generation-report-summary">
                  <div className="execution-report-content">
                    <article className="execution-report-narrative">
                      <div className="execution-report-label">
                        <BookOpen size={15} />
                        <div>
                          <b>课程边界计划</b>
                          <small>本课范围与内容边界</small>
                        </div>
                      </div>
                      <p
                        title={
                          generationReport.draft?.generationPlan ||
                          "暂无课程计划快照"
                        }
                      >
                        {formatGenerationSnapshot(
                          generationReport.draft?.generationPlan,
                          "暂无课程计划快照",
                        )}
                      </p>
                    </article>
                    <article className="execution-report-narrative">
                      <div className="execution-report-label">
                        <ClipboardCheck size={15} />
                        <div>
                          <b>公众号保留清单</b>
                          <small>转换时必须保留的知识点</small>
                        </div>
                      </div>
                      <p
                        title={
                          generationReport.wechat?.contentManifest ||
                          "暂无内容清单快照"
                        }
                      >
                        {formatGenerationSnapshot(
                          generationReport.wechat?.contentManifest,
                          "暂无内容清单快照",
                        )}
                      </p>
                    </article>
                  </div>
                </section>
              )}

              {selected.status === "failed" && (
                <div className="execution-error">
                  <AlertTriangle size={17} />
                  <div>
                    <strong>失败步骤：{failedStepTitle}</strong>
                    <p>{selected.errorMessage || "任务执行失败"}</p>
                  </div>
                  <button onClick={() => onRetry(selected.id)}>
                    重新执行失败步骤
                  </button>
                </div>
              )}

              <div className="execution-pipeline">
                {uniqueGenerationPipeline.map(
                  ([key, title, description], index) => {
                    const stepEvents = mappedEvents.filter(
                      (item) => item.stepIndex === index,
                    );
                    const stepFailedByEvent = stepEvents.some(({ event }) =>
                      isExecutionFailureEvent(event.eventType),
                    );
                    const failed =
                      (selected.status === "failed" || stepFailedByEvent) &&
                      index === activeIndex &&
                      !isExecuting;
                    const running = isExecuting && index === activeIndex;
                    const complete =
                      !failed &&
                      (selected.status === "succeeded" || index < activeIndex);
                    const retrying = running && isRetrying;
                    const latest = running
                      ? [...stepEvents]
                          .reverse()
                          .find(
                            ({ event }) =>
                              !isExecutionFailureEvent(event.eventType),
                          )?.event
                      : stepEvents[stepEvents.length - 1]?.event;
                    const eventMessage = running
                      ? latest?.message ||
                        `${retrying ? "正在重新执行" : "正在执行"}“${title}”`
                      : latest && complete
                        ? completedExecutionMessage(latest.message)
                        : latest?.message;
                    return (
                      <div
                        className={`execution-step ${complete ? "complete" : retrying ? "retrying" : running ? "running" : failed ? "failed" : "pending"}`}
                        key={key}
                      >
                        <div className="execution-node">
                          {complete ? (
                            <CheckCircle2 size={17} />
                          ) : failed ? (
                            <AlertTriangle size={17} />
                          ) : running ? (
                            <RefreshCcw size={17} />
                          ) : (
                            <span />
                          )}
                        </div>
                        <div className="execution-step-copy">
                          <div>
                            <strong>
                              {index + 1}. {title}
                            </strong>
                            <span>
                              {complete
                                ? "已完成"
                                : retrying
                                  ? "正在重试"
                                  : running
                                    ? "执行中"
                                    : failed
                                      ? "执行失败"
                                      : "等待执行"}
                            </span>
                          </div>
                          <p>{description}</p>
                          {(latest || running) && (
                            <small>
                              {eventMessage}
                              {latest
                                ? ` · ${formatExecutionTime(latest.createdAt)}`
                                : ""}
                            </small>
                          )}
                        </div>
                      </div>
                    );
                  },
                )}
              </div>

              <div className="execution-actions">
                <button
                  aria-expanded={showSkills}
                  onClick={() => setShowSkills((value) => !value)}
                >
                  {showSkills ? (
                    <ChevronDown size={14} />
                  ) : (
                    <ChevronRight size={14} />
                  )}
                  本次使用的 Skill
                </button>
                <button
                  aria-expanded={showNodeDetails}
                  onClick={() => setShowNodeDetails((value) => !value)}
                >
                  {showNodeDetails ? (
                    <ChevronDown size={14} />
                  ) : (
                    <ChevronRight size={14} />
                  )}
                  技术执行明细（{attemptNodes.length} 个节点）
                </button>
                <button
                  aria-expanded={showEvents}
                  onClick={() => setShowEvents((value) => !value)}
                >
                  {showEvents ? (
                    <ChevronDown size={14} />
                  ) : (
                    <ChevronRight size={14} />
                  )}
                  事件日志（{attemptEvents.length} 条）
                </button>
              </div>

              {attemptBatches.length > 1 && (showNodeDetails || showEvents) && (
                <ExecutionAttemptSwitcher
                  batches={attemptBatches}
                  selectedNumber={selectedAttempt?.number}
                  onSelect={setSelectedAttemptNumber}
                  formatTime={formatExecutionTime}
                  isFailureEvent={isExecutionFailureEvent}
                />
              )}

              {showNodeDetails && (
                <section className="execution-subsection workflow-node-section">
                  <div className="execution-subsection-heading">
                    <div>
                      <h4>技术执行明细</h4>
                      <p>
                        用于排查模型调用、分段生成和节点重试；上方流程图用于查看整体进度。
                      </p>
                    </div>
                    <span>
                      第 {selectedAttempt?.number || 1} 次 ·{" "}
                      {attemptNodes.length} 个节点
                    </span>
                  </div>
                  {attemptNodes.length ? (
                    <div className="workflow-node-list">
                      {groupedNodes.map(({ parent, children }) => (
                        <div
                          className={`workflow-node-group ${children.length ? "has-children" : ""}`}
                          key={parent.id}
                        >
                          <WorkflowNodeRow node={parent} />
                          {children.length > 0 && (
                            <div
                              className="workflow-node-children"
                              aria-label={`${workflowNodeTitle(parent.nodeKey)}的子步骤`}
                            >
                              <div className="workflow-node-children-label">
                                子步骤 · {children.length}
                              </div>
                              {children.map((node) => (
                                <WorkflowNodeRow
                                  child
                                  node={node}
                                  key={node.id}
                                />
                              ))}
                            </div>
                          )}
                        </div>
                      ))}
                    </div>
                  ) : (
                    <p>旧任务没有节点级记录，可继续查看下方完整事件记录。</p>
                  )}
                </section>
              )}

              {showSkills && (
                <section className="execution-subsection">
                  <h4>本次使用的 Skill</h4>
                  <div className="execution-skill-list">
                    {skills.length ? (
                      skills.map((skill, index) => (
                        <div key={skill.id || `${skill.name}-${index}`}>
                          <b>{skill.name || "未命名 Skill"}</b>
                          <span>
                            {skill.scope === "system"
                              ? "系统级"
                              : `顺序 ${skill.priority ?? "-"}`}
                          </span>
                        </div>
                      ))
                    ) : (
                      <p>旧任务没有保存 Skill 快照。</p>
                    )}
                  </div>
                </section>
              )}

              <section className="execution-subsection version-links">
                <h4>关联文章版本</h4>
                {versions.length ? (
                  <div>
                    {versions.map((version) => (
                      <span key={version.id}>
                        {`文章版本 v${version.versionNo} · ${version.reason}`}
                      </span>
                    ))}
                  </div>
                ) : (
                  <p>
                    该轨迹没有关联的 AI 文章版本；手动修改、恢复和导入版本不会关联。
                  </p>
                )}
              </section>

              {showEvents && (
                <section className="execution-subsection">
                  <div className="execution-subsection-heading">
                    <div>
                      <h4>技术事件日志</h4>
                      <p>
                        保留后台上报的原始事件，主要用于定位异常，不代表额外执行了一套流程。
                      </p>
                    </div>
                    <span>
                      第 {selectedAttempt?.number || 1} 次 ·{" "}
                      {attemptEvents.length} 条事件
                    </span>
                  </div>
                  <div className="execution-event-list">
                    {attemptEvents.map((event) => {
                      const isCurrentEvent =
                        event.id === latestEvent?.id && isExecuting;
                      const message = isCurrentEvent
                        ? event.message
                        : technicalEventMessage(event, attemptEvents);
                      return (
                        <div key={event.id}>
                          <time>{formatExecutionTime(event.createdAt)}</time>
                          <b title={`内部标识：${event.step}`}>
                            {eventStepTitle(event.step)}
                          </b>
                          <span>{message}</span>
                        </div>
                      );
                    })}
                  </div>
                </section>
              )}
            </>
          ) : (
            <div className="execution-empty large">
              {selectedJobId
                ? "正在加载该任务的执行轨迹…"
                : "选择一条生成记录查看完整执行线路"}
            </div>
          )}
        </div>
      </div>
    </section>
  );
}
