// @ts-nocheck
import {
  AlertTriangle,
  ArrowLeft,
  CheckCircle2,
  ChevronDown,
  ChevronRight,
  History,
  RefreshCcw,
} from "lucide-react";
import { useEffect, useMemo, useRef, useState } from "react";
import type { JobData, JobEventData, WorkflowNodeData } from "../../../shared/api/jobs";
import {
  loadJobEventHistory,
  loadJobNodes,
  retryFailedJob,
} from "../../../shared/api/jobs";
import { ExecutionAttemptSwitcher } from "../../../shared/components/ExecutionAttemptSwitcher";
import { formatElapsedDurationLabel } from "../../../shared/format/duration";
import { formatExecutionNodeTime } from "../../../shared/format/time";
import {
  imageCountLabel,
  wordCountLabel,
} from "../../../shared/format/wordImageLabels";
import { usePolling } from "../../../shared/hooks/usePolling";
import {
  getHotNewsArticle,
  getHotNewsArticleSkeleton,
  listHotNewsArticleGenerationRuns,
  resolveHotNewsDocumentIdFromLocation,
  withDocumentIdQuery,
} from "../api";
import {
  toImageStepFailureMessage,
  toUserFailureMessage,
} from "../../../shared/job-failure-message";
import type {
  HotNewsArticle,
  HotNewsArticleSkeleton,
} from "../api";
import {
  stepClassName,
  normalizeHotNewsNodeStatus,
  isHotNewsExecutionFailureEvent,
  buildHotNewsExecutionAttemptBatches,
  getHotNewsStageDisplayStatus,
  resolveHotNewsImageProgressMessage,
  HotNewsWorkflowNode,
  stages,
  labels,
  runningNotes,
  statusText,
  jobDisplayStatus,
  time,
  duration,
  text,
  readableOutput,
  parent,
} from "./hotNewsExecutionHelpers";

/** 由页面拆出的视图。 */
export function HotNewsExecutionView(props: Record<string, any>) {
  const {
    HISTORY_PAGE_SIZE,
    article,
    attemptBatches,
    attemptEvents,
    attemptNodes,
    currentStageIndex,
    currentStageKey,
    effectiveCurrentStageKey,
    events,
    goBackToWorkspace,
    historyPageCount,
    loading,
    nodes,
    note,
    open,
    pagedJobs,
    refresh,
    safeHistoryPage,
    selected,
    selectedAttempt,
    selectedId,
    setHistoryPage,
    setOpen,
    setSelectedAttemptNumber,
    setSelectedId,
    skeleton,
    skills,
    state,
    visibleJobs,
  } = props;
  return (
    <section className="execution-page hot-execution-page">
      <header className="execution-heading">
        <div>
          <span className="section-kicker">Execution Trace</span>
          <h2>热点文章内容执行轨迹</h2>
          <p>查看每次生成的业务流程、节点结果、Skill 快照和文章产物。</p>
        </div>
        <button
          className="ghost-button"
          onClick={() => void goBackToWorkspace()}
        >
          <ArrowLeft size={15} />
          返回文章工作区
        </button>
      </header>
      <div className="execution-layout">
        <aside className="execution-history">
          <div className="execution-panel-title">
            <History size={16} />
            生成记录
          </div>
          <div className="execution-history-list">
            {pagedJobs.length ? (
              pagedJobs.map((j) => (
                <button
                  className={`execution-record ${j.id === selectedId ? "active" : ""}`}
                  key={j.id}
                  onClick={() => setSelectedId(j.id)}
                >
                  <span className={`execution-state state-${jobDisplayStatus(j)}`}>
                    {statusText(jobDisplayStatus(j))}
                  </span>
                  <strong>{article?.title || "热点文章"}</strong>
                  <small>{time(j.createdAt)}</small>
                </button>
              ))
            ) : (
              <div className="execution-empty">暂无生成记录</div>
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
        <main className="execution-detail">
          {loading ? (
            <div className="execution-empty large">
              <RefreshCcw size={18} />
              正在读取执行轨迹
            </div>
          ) : selected ? (
            <>
              <div className="execution-summary">
                <div>
                  <span>热点资讯模式 · 生成记录</span>
                  <h3>{article?.title}</h3>
                </div>
                <span className={`execution-state state-${displayStatus}`}>
                  {statusText(displayStatus)}
                </span>
              </div>
              <section className="hot-research-skeleton hot-execution-skeleton">
                {skeleton?.coreThesis ? (
                  <>
                    <div className="hot-research-skeleton-head">
                      <strong>文章骨架</strong>
                      <span>{skeleton.workingTitle || "未命名"}</span>
                    </div>
                    <p>
                      <b>范围：</b>
                      {skeleton.scope || "—"}
                    </p>
                    <p>
                      <b>核心：</b>
                      {skeleton.coreThesis}
                    </p>
                    {skeleton.narrativeShape ? (
                      <p>
                        <b>叙事：</b>
                        {skeleton.narrativeShape}
                      </p>
                    ) : null}
                    {skeleton.architecture?.length ? (
                      <p>
                        <b>架构：</b>
                        {skeleton.architecture.join(" → ")}
                      </p>
                    ) : null}
                    {skeleton.sectionPlan?.length ? (
                      <p>
                        <b>段落：</b>
                        {skeleton.sectionPlan.join(" → ")}
                      </p>
                    ) : null}
                    <div className="hot-research-skeleton-generation">
                      <p>
                        <b>推荐字数：</b>
                        {wordCountLabel(skeleton.recommendedWordCountOption)}
                      </p>
                      <p>
                        <b>正文插图：</b>
                        {imageCountLabel(skeleton.recommendedImageCountOption)}
                        <span className="hot-research-skeleton-note">
                          （另含封面 1 张）
                        </span>
                      </p>
                      <p>
                        <b>插图风格：</b>
                        {skeleton.illustrationStyle || "—"}
                      </p>
                      {skeleton.coverBrief ? (
                        <p>
                          <b>封面要点：</b>
                          {skeleton.coverBrief}
                        </p>
                      ) : null}
                    </div>
                    {skeleton.modules?.length ? (
                      <ul>
                        {skeleton.modules.map((module, index) => (
                          <li key={`${String(module.name)}-${index}`}>
                            <strong>
                              {String(module.name || `模块 ${index + 1}`)}
                            </strong>
                            {module.purpose
                              ? ` — ${String(module.purpose)}`
                              : ""}
                          </li>
                        ))}
                      </ul>
                    ) : null}
                  </>
                ) : (
                  <div className="hot-research-skeleton-head">
                    <strong>文章骨架</strong>
                    <span>尚未拟定，请先在资料池生成骨架</span>
                  </div>
                )}
              </section>
              {displayStatus === "failed" && (
                <div className="execution-error">
                  <AlertTriangle size={17} />
                  <div>
                    <strong>生成任务执行失败</strong>
                    <p>
                      {(currentStageKey === "hot_news_generate_images"
                        ? toImageStepFailureMessage(
                            selected.errorMessage,
                            resolveHotNewsImageProgressMessage(attemptEvents),
                          )
                        : toUserFailureMessage(selected.errorMessage)) ||
                        "请查看失败节点和事件日志。"}
                    </p>
                  </div>
                  <button
                    onClick={() =>
                      void retryFailedJob(selected.id).then((job) => {
                        setSelectedId(job.id);
                        return refresh();
                      })
                    }
                  >
                    重新执行失败步骤
                  </button>
                </div>
              )}
              <div className="execution-pipeline">
                {stages.map(([key, title, description], i) => {
                  const persistedState = state(key);
                  const s = getHotNewsStageDisplayStatus({
                    persistedState,
                    jobStatus: displayStatus,
                    stageIndex: i,
                    currentStageIndex,
                    isCurrentStage: effectiveCurrentStageKey === key,
                  });
                  const liveProgressMessage =
                    key === "hot_news_generate_images"
                      ? resolveHotNewsImageProgressMessage(attemptEvents)
                      : undefined;
                  const stageNote =
                    persistedState === "succeeded" || persistedState === "failed"
                      ? note(key)
                      : displayStatus === "failed" && currentStageKey === key
                      ? (key === "hot_news_generate_images"
                          ? toImageStepFailureMessage(
                              selected.errorMessage,
                              liveProgressMessage,
                            )
                          : toUserFailureMessage(selected.errorMessage)) ||
                        note(key)
                      : displayStatus === "running" &&
                          effectiveCurrentStageKey === key
                        ? liveProgressMessage || runningNotes[key]
                        : (displayStatus === "running" ||
                              displayStatus === "failed") &&
                          currentStageIndex >= 0 &&
                          i > currentStageIndex
                        ? ""
                        : note(key);
                  const showLiveNote =
                    Boolean(stageNote) || s === "running";
                  const liveMessage =
                    stageNote ||
                    (s === "running"
                      ? liveProgressMessage ||
                        runningNotes[key] ||
                        `正在执行“${title}”`
                      : "");
                  return (
                    <div
                      className={`execution-step ${stepClassName(s)}`}
                      key={key}
                    >
                      <div className="execution-node">
                        {s === "succeeded" ? (
                          <CheckCircle2 size={17} />
                        ) : s === "failed" ? (
                          <AlertTriangle size={17} />
                        ) : s === "running" ? (
                          <RefreshCcw size={17} />
                        ) : (
                          <span />
                        )}
                      </div>
                      <div className="execution-step-copy">
                        <div>
                          <strong>
                            {i + 1}. {title}
                          </strong>
                          <span>{statusText(s)}</span>
                        </div>
                        <p>{description}</p>
                        {showLiveNote && liveMessage ? (
                          <small>{liveMessage}</small>
                        ) : null}
                      </div>
                    </div>
                  );
                })}
              </div>
              <div className="execution-actions">
                <button
                  aria-expanded={open.skills}
                  onClick={() => setOpen((x) => ({ ...x, skills: !x.skills }))}
                >
                  {open.skills ? (
                    <ChevronDown size={14} />
                  ) : (
                    <ChevronRight size={14} />
                  )}
                  本次使用的 Skill
                </button>
                <button
                  aria-expanded={open.nodes}
                  onClick={() => setOpen((x) => ({ ...x, nodes: !x.nodes }))}
                >
                  {open.nodes ? (
                    <ChevronDown size={14} />
                  ) : (
                    <ChevronRight size={14} />
                  )}
                  技术执行明细（{attemptNodes.length} 个节点）
                </button>
                <button
                  aria-expanded={open.events}
                  onClick={() => setOpen((x) => ({ ...x, events: !x.events }))}
                >
                  {open.events ? (
                    <ChevronDown size={14} />
                  ) : (
                    <ChevronRight size={14} />
                  )}
                  事件日志（{attemptEvents.length} 条）
                </button>
              </div>
              {attemptBatches.length > 1 && (open.nodes || open.events) && (
                <ExecutionAttemptSwitcher
                  batches={attemptBatches}
                  selectedNumber={selectedAttempt.number}
                  onSelect={setSelectedAttemptNumber}
                  formatTime={time}
                  isFailureEvent={isHotNewsExecutionFailureEvent}
                  buttonType="button"
                />
              )}
              {open.skills && (
                <section className="execution-subsection">
                  <h4>本次使用的 Skill</h4>
                  <div className="execution-skill-list">
                    {skills.length ? (
                      skills.map((s, i) => (
                        <div key={String(s.id || i)}>
                          <b>{String(s.name || s.id || "热点文章 Skill")}</b>
                          <span>
                            {String(s.scope || `顺序 ${s.priority ?? i + 1}`)}
                          </span>
                        </div>
                      ))
                    ) : (
                      <p>当前记录没有保存 Skill 快照。</p>
                    )}
                  </div>
                </section>
              )}
              {open.nodes && (
                <section className="execution-subsection">
                  <div className="execution-subsection-heading">
                    <div>
                      <h4>技术执行明细</h4>
                      <p>
                        用于排查模型调用与节点重试；上方流程图用于查看整体进度。
                      </p>
                    </div>
                    <span>
                      第 {selectedAttempt.number || 1} 次 ·{" "}
                      {attemptNodes.length} 个节点
                    </span>
                  </div>
                  <div className="hot-technical-node-list">
                    {attemptNodes.length ? (
                      attemptNodes.map((n) => (
                      <div key={n.id}>
                        <span className={`execution-state state-${normalizeHotNewsNodeStatus(n)}`}>
                          {statusText(normalizeHotNewsNodeStatus(n))}
                        </span>
                        <strong>
                          {labels[n.nodeKey] ||
                            `内部步骤：${n.nodeKey.replace(/^hot_news_/, "").replaceAll("_", " ")}`}
                        </strong>
                        <small>
                          第 {n.attemptNo} 次 · {time(n.startedAt)} · {duration(n.startedAt, n.finishedAt)}
                        </small>
                        <p>
                          {n.errorMessage
                            ? toUserFailureMessage(n.errorMessage)
                            : `输出：${readableOutput(n.outputSummary, "节点已完成，未保存输出摘要")}`}
                        </p>
                        {n.inputSummary && (
                          <p className="execution-node-input">输入：{text(n.inputSummary, "无输入摘要")}</p>
                        )}
                      </div>
                      ))
                    ) : (
                      <p>当前批次还没有节点记录。</p>
                    )}
                  </div>
                </section>
              )}
              {open.events && (
                <section className="execution-subsection">
                  <div className="execution-subsection-heading">
                    <div>
                      <h4>技术事件日志</h4>
                      <p>
                        保留后台上报的原始事件，主要用于定位异常，不代表额外执行了一套流程。
                      </p>
                    </div>
                    <span>
                      第 {selectedAttempt.number || 1} 次 ·{" "}
                      {attemptEvents.length} 条事件
                    </span>
                  </div>
                  <div className="execution-event-list">
                    {attemptEvents.map((e) => (
                      <div key={e.id}>
                        <time>{time(e.createdAt)}</time>
                        <b>{labels[parent(e.step) || ""] || e.step}</b>
                        <span>{e.message}</span>
                      </div>
                    ))}
                  </div>
                </section>
              )}
            </>
          ) : (
            <div className="execution-empty large">
              暂无生成记录，请先在文章工作区发起生成。
            </div>
          )}
        </main>
      </div>
    </section>
  );
}
