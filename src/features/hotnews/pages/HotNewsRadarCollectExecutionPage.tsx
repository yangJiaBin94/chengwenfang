import {
  AlertTriangle,
  ArrowLeft,
  CheckCircle2,
  ChevronDown,
  ChevronRight,
  History,
  RefreshCcw,
} from "lucide-react";
import { useEffect, useMemo, useState } from "react";
import type { JobData, JobEventData, WorkflowNodeData } from "../../../shared/api/jobs";
import {
  loadJobEventHistory,
  loadJobNodes,
  retryFailedJob,
} from "../../../shared/api/jobs";
import { formatElapsedDurationLabel } from "../../../shared/format/duration";
import { formatExecutionNodeTime } from "../../../shared/format/time";
import { usePolling } from "../../../shared/hooks/usePolling";
import { listHotNewsRadarCollectRuns } from "../api";
import { toUserFailureMessage } from "../../../shared/job-failure-message";
import {
  buildHotNewsExecutionAttemptBatches,
  getHotNewsStageDisplayStatus,
  normalizeHotNewsNodeStatus,
} from "./HotNewsExecutionPage";

type HotNewsWorkflowNode = WorkflowNodeData;

const stages = [
  ["radar_load_context", "读取采集上下文", "读取主题、渠道与视频解析配额。"],
  ["radar_load_skills", "加载采集 Skill", "固化本流程 Skill 与提示词快照。"],
  [
    "radar_fetch_channels",
    "获取渠道资源",
    "并行拉取各渠道，过滤不可直连 URL，再按文章 50 / 视频 10 配额裁剪。",
  ],
  [
    "radar_enrich_summarize",
    "解析与事实速览",
    "对配额内保留的候选取正文/视频解析，并生成事实速览（原文不落库）。",
  ],
  [
    "radar_candidate_filter",
    "素材筛选",
    "按主题相关性与营销等规则过滤候选，弃用只记日志。",
  ],
  ["radar_persist_cluster", "落库与成簇", "幸存者入库、成簇，并合并簇摘要。"],
  [
    "radar_ai_theme_seed",
    "AI 主题素材",
    "按主题问卷生成 AI 种子素材；本批不足 10 条时补满至 10，已满则跳过，不走渠道抓取。",
  ],
  ["radar_score_top10", "评分取 Top10", "按写作分排序并标记本轮前十条。"],
  ["radar_finalize", "完成采集收尾", "关闭采集运行并汇总计数。"],
] as const;

const labels: Record<string, string> = {
  ...Object.fromEntries(stages.map(([key, title]) => [key, title])),
  // 兼容历史执行记录里的旧节点键
  radar_marketing_filter: "素材筛选",
};
const runningNotes: Record<string, string> = {
  radar_load_context: "正在读取主题与渠道配置...",
  radar_load_skills: "正在加载采集 Skill...",
  radar_fetch_channels: "正在并行采集各渠道...",
  radar_enrich_summarize: "正在解析正文/视频并生成事实速览...",
  radar_candidate_filter: "正在按主题与营销规则筛选素材...",
  radar_marketing_filter: "正在按主题与营销规则筛选素材...",
  radar_persist_cluster: "正在落库、成簇并合并摘要...",
  radar_ai_theme_seed: "正在生成/补全 AI 主题素材...",
  radar_score_top10: "正在评分并标记 Top10...",
  radar_finalize: "正在完成采集收尾...",
};

/** 将 Job.currentStep 归一到雷达采集阶段键。 */
export function resolveRadarCollectStageKey(
  step?: string | null,
): string | null {
  if (!step) return null;
  if (labels[step]) return step;
  if (step === "radar_marketing_filter") return "radar_candidate_filter";
  if (step.startsWith("radar_")) {
    const match = stages.find(([stageKey]) => step.startsWith(stageKey));
    return match?.[0] ?? null;
  }
  return null;
}

/** 外层雷达页：采集任务进度文案（阶段名 + 可选 progressMessage）。 */
export function formatRadarCollectOuterStatus(
  job: Pick<JobData, "status" | "currentStep" | "progress" | "progressMessage">,
): { stageTitle: string; message: string; progress: number } {
  const stageKey = resolveRadarCollectStageKey(job.currentStep);
  const stageTitle =
    (stageKey && labels[stageKey]) ||
    (job.status === "pending" ? "等待执行" : "雷达采集");
  const detail =
    (typeof job.progressMessage === "string" && job.progressMessage.trim()) ||
    (stageKey && runningNotes[stageKey]) ||
    "";
  const progress = Math.max(0, Math.min(100, Number(job.progress || 0)));
  const message = detail
    ? `${stageTitle} · ${detail}`
    : job.status === "pending"
      ? "雷达采集任务正在排队"
      : `正在执行：${stageTitle}`;
  return { stageTitle, message, progress };
}

/**
 * 解析步骤进度文案：取「已解析 i/N」进度最高的一条（兼容旧「第 i/N 条」）。
 */
export function resolveRadarEnrichProgressMessage(
  events: Pick<JobEventData, "id" | "step" | "message">[],
): string | undefined {
  return resolveRadarStageProgressMessage(events, "radar_enrich_summarize");
}

/**
 * 取指定步骤最新进度文案；解析步骤按已解析条数优先。
 */
export function resolveRadarStageProgressMessage(
  events: Pick<JobEventData, "id" | "step" | "message">[],
  stageKey: string,
): string | undefined {
  const matched = events.filter(
    (event) =>
      (event.step === stageKey || event.step?.startsWith(`${stageKey}`)) &&
      typeof event.message === "string" &&
      event.message.trim(),
  );
  if (!matched.length) return undefined;
  if (stageKey === "radar_enrich_summarize") {
    const parsed = matched.map((event) => {
      const doneMatch =
        event.message.match(/已解析\s*(\d+)\s*\/\s*(\d+)/) ||
        event.message.match(/第\s*(\d+)\s*\/\s*(\d+)\s*条/);
      if (!doneMatch) {
        return { id: event.id, slotIndex: 0, message: event.message };
      }
      return {
        id: event.id,
        slotIndex: Number(doneMatch[1]),
        message: event.message,
      };
    });
    parsed.sort(
      (left, right) =>
        right.slotIndex - left.slotIndex || right.id - left.id,
    );
    return parsed[0]?.message;
  }
  return [...matched].sort((left, right) => right.id - left.id)[0]?.message;
}

/** 从节点 outputSummary 读取指定摘要字段。 */
function resolveRadarStageDoneNote(
  nodes: HotNewsWorkflowNode[],
  field: string,
): string | undefined {
  for (const item of [...nodes].reverse()) {
    const summary = item.outputSummary;
    if (!summary || typeof summary !== "object") continue;
    const note = (summary as Record<string, unknown>)[field];
    if (typeof note === "string" && note.trim()) {
      return note.trim();
    }
  }
  return undefined;
}

/** 从节点 outputSummary 读取解析完成摘要。 */
function resolveRadarEnrichDoneNote(
  nodes: HotNewsWorkflowNode[],
): string | undefined {
  const fromField = resolveRadarStageDoneNote(nodes, "enrichNote");
  if (fromField) return fromField;
  const node = [...nodes]
    .reverse()
    .find(
      (item) =>
        item.nodeKey === "radar_enrich_summarize" ||
        item.nodeKey?.startsWith("radar_enrich_summarize"),
    );
  const summary = node?.outputSummary;
  if (!summary || typeof summary !== "object") return undefined;
  const total = Number(summary.candidateCount);
  const discarded = Number(summary.enrichDiscardedCount);
  const articles = Number(summary.enrichArticleCount);
  const videos = Number(summary.enrichVideoCount);
  const survivors = Number(summary.survivorCount);
  if (
    Number.isFinite(total) &&
    total >= 0 &&
    Number.isFinite(discarded) &&
    Number.isFinite(survivors)
  ) {
    const typePart =
      Number.isFinite(articles) && Number.isFinite(videos)
        ? `（文章 ${articles} 条，视频 ${videos} 条）`
        : "";
    return `解析完成：共 ${total} 条${typePart}，成功 ${survivors}，弃用 ${discarded}`;
  }
  return undefined;
}

const statusText = (s: string) =>
  s === "succeeded"
    ? "已完成"
    : s === "failed"
      ? "执行失败"
      : s === "running"
        ? "执行中"
        : s === "cancelled"
          ? "已取消"
          : "等待执行";

function stepClassName(displayStatus: string) {
  if (displayStatus === "succeeded") return "complete";
  if (displayStatus === "failed") return "failed";
  if (displayStatus === "running") return "running";
  return "pending";
}

const jobDisplayStatus = (job: Pick<JobData, "status" | "currentStep">) =>
  job.status === "pending" &&
  (job.currentStep === "retry_queued" || job.currentStep === "recovered")
    ? "running"
    : job.status;

const time = formatExecutionNodeTime;
const duration = formatElapsedDurationLabel;

function parent(key: string) {
  return resolveRadarCollectStageKey(key);
}

/** 节点输出摘要：优先可读字段，否则截断展示。 */
function readableRadarOutput(value: unknown, fallback: string) {
  if (!value || typeof value !== "object" || Array.isArray(value)) {
    return fallback;
  }
  const data = value as Record<string, unknown>;
  const parts: string[] = [];
  for (const field of [
    "fetchNote",
    "enrichNote",
    "filterNote",
    "persistNote",
    "skillSnapshotSummary",
  ] as const) {
    const text = data[field];
    if (typeof text === "string" && text.trim()) {
      parts.push(text.trim());
    }
  }
  if (typeof data.candidateCount === "number") {
    parts.push(`候选 ${data.candidateCount} 条`);
  }
  if (typeof data.survivorCount === "number") {
    parts.push(`幸存 ${data.survivorCount} 条`);
  }
  if (typeof data.insertedCount === "number") {
    parts.push(`落库 ${data.insertedCount} 条`);
  }
  if (typeof data.topicCount === "number") {
    parts.push(`选题 ${data.topicCount} 个`);
  }
  if (typeof data.top10Count === "number") {
    parts.push(`Top10 ${data.top10Count}`);
  }
  if (parts.length) return parts.join("；");
  const entries = Object.entries(data)
    .filter(([key]) => key !== "leaseRenewal" && key !== "channelIds")
    .slice(0, 4)
    .map(([key, item]) =>
      `${key}：${Array.isArray(item) ? `${item.length} 项` : String(item)}`,
    );
  return entries.join("；") || fallback;
}

/** 事件 step 展示名。 */
function eventStepLabel(step?: string | null) {
  if (!step) return "—";
  const parentKey = parent(step);
  if (parentKey && labels[parentKey]) return labels[parentKey];
  if (labels[step]) return labels[step];
  const pipeline: Record<string, string> = {
    queued: "排队",
    started: "开始",
    graph_execute: "执行工作流",
    graph_finalize: "汇总结果",
    done: "完成",
    succeeded: "成功",
    failed: "失败",
  };
  return pipeline[step] || step;
}

export function HotNewsRadarCollectExecutionPage({
  themeId,
  themeSlug,
  themeName,
  navigate,
}: {
  themeId: string;
  themeSlug: string;
  themeName: string;
  navigate: (path: string) => void;
}) {
  const [jobs, setJobs] = useState<JobData[]>([]);
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [nodes, setNodes] = useState<HotNewsWorkflowNode[]>([]);
  const [events, setEvents] = useState<JobEventData[]>([]);
  const [historyPage, setHistoryPage] = useState(1);
  const HISTORY_PAGE_SIZE = 10;
  const [open, setOpen] = useState({ nodes: false, events: false });
  const [selectedAttemptNumber, setSelectedAttemptNumber] = useState<
    number | null
  >(null);
  const [loading, setLoading] = useState(true);

  const refresh = async () => {
    const list = await listHotNewsRadarCollectRuns(themeId);
    setJobs(list);
    const jobFromQuery = new URLSearchParams(window.location.search).get("job");
    setSelectedId((current) => {
      if (current && list.some((row) => row.id === current)) return current;
      if (jobFromQuery && list.some((row) => row.id === jobFromQuery)) {
        return jobFromQuery;
      }
      return list[0]?.id || null;
    });
  };

  useEffect(() => {
    void refresh().finally(() => setLoading(false));
  }, [themeId]);

  usePolling(() => refresh(), 2500, [themeId]);

  const selected = jobs.find((job) => job.id === selectedId) || null;

  useEffect(() => {
    if (!selected?.id) {
      setNodes([]);
      setEvents([]);
      return;
    }
    let cancelled = false;
    void Promise.all([
      loadJobNodes(selected.id),
      loadJobEventHistory(selected.id),
    ]).then(([nodeRows, eventRows]) => {
      if (cancelled) return;
      setNodes(nodeRows);
      setEvents(eventRows);
    });
    return () => {
      cancelled = true;
    };
  }, [
    selected?.id,
    selected?.status,
    selected?.currentStep,
    selected?.progress,
    selected?.progressMessage,
  ]);

  const attemptBatches = useMemo(
    () => buildHotNewsExecutionAttemptBatches(events, nodes),
    [events, nodes],
  );
  const selectedAttempt =
    attemptBatches.find((batch) => batch.number === selectedAttemptNumber) ||
    attemptBatches[attemptBatches.length - 1];
  const attemptNodes = selectedAttempt?.nodes || [];
  const attemptEvents = selectedAttempt?.events || [];

  useEffect(() => {
    setSelectedAttemptNumber(null);
  }, [selected?.id]);

  const state = (key: string) => {
    const related = attemptNodes.filter(
      (node) => parent(node.nodeKey) === key || node.nodeKey === key,
    );
    if (!related.length) return "pending";
    if (related.some((node) => normalizeHotNewsNodeStatus(node) === "failed")) {
      return "failed";
    }
    if (related.every((node) => normalizeHotNewsNodeStatus(node) === "succeeded")) {
      return "succeeded";
    }
    if (related.some((node) => normalizeHotNewsNodeStatus(node) === "running")) {
      return "running";
    }
    return "pending";
  };

  const displayStatus = selected ? jobDisplayStatus(selected) : "pending";
  // FormalGraph 以 job.currentStep 为准，避免节点明细刷新滞后时仍停在上一阶段
  const jobStageKey = selected?.currentStep
    ? parent(selected.currentStep) ||
      (labels[selected.currentStep] ? selected.currentStep : null)
    : null;
  const nodeRunningKey =
    stages.find(([key]) => state(key) === "running")?.[0] || null;
  const currentStageKey =
    (displayStatus === "running" ? jobStageKey || nodeRunningKey : nodeRunningKey) ||
    stages.find(([key]) => state(key) === "failed")?.[0] ||
    null;
  const currentStageIndex = currentStageKey
    ? stages.findIndex(([key]) => key === currentStageKey)
    : -1;
  const effectiveCurrentStageKey =
    displayStatus === "running"
      ? currentStageKey ||
        stages.find(([key]) => state(key) === "pending")?.[0] ||
        null
      : currentStageKey;

  const historySlice = jobs.slice(
    (historyPage - 1) * HISTORY_PAGE_SIZE,
    historyPage * HISTORY_PAGE_SIZE,
  );
  const historyPages = Math.max(1, Math.ceil(jobs.length / HISTORY_PAGE_SIZE));

  const note = (key: string) => {
    const related = attemptNodes.filter(
      (node) => parent(node.nodeKey) === key || node.nodeKey === key,
    );
    const failed = related.find(
      (node) => normalizeHotNewsNodeStatus(node) === "failed",
    );
    if (failed?.errorMessage) return failed.errorMessage;
    if (key === "radar_enrich_summarize") {
      return resolveRadarEnrichDoneNote(related) || "";
    }
    if (key === "radar_fetch_channels") {
      return resolveRadarStageDoneNote(related, "fetchNote") || "";
    }
    if (key === "radar_candidate_filter") {
      return resolveRadarStageDoneNote(related, "filterNote") || "";
    }
    if (key === "radar_persist_cluster") {
      return resolveRadarStageDoneNote(related, "persistNote") || "";
    }
    if (key === "radar_ai_theme_seed") {
      return resolveRadarStageDoneNote(related, "aiThemeSeedNote") || "";
    }
    if (key === "radar_score_top10") {
      return resolveRadarStageDoneNote(related, "scoreTop10Note") || "";
    }
    return "";
  };

  const radarPath = `/modes/hot-news/themes/${encodeURIComponent(themeSlug || themeId)}/radar`;

  if (loading) {
    return (
      <section className="execution-page">
        <header className="execution-heading">
          <h2>雷达采集执行</h2>
          <p>正在加载执行记录...</p>
        </header>
      </section>
    );
  }

  return (
    <section className="execution-page">
      <header className="execution-heading">
        <div>
          <span className="section-kicker">热点模式 · 雷达采集</span>
          <h2>{themeName || "热点雷达采集"}</h2>
          <p>按工作流节点同步执行：获取 → 解析速览 → 筛选 → 成簇 → AI 主题素材 → Top10。</p>
        </div>
        <div className="execution-heading-actions">
          <button
            type="button"
            className="ghost-button"
            onClick={() => navigate(radarPath)}
          >
            <ArrowLeft size={15} />
            返回雷达
          </button>
          <button
            type="button"
            className="ghost-button"
            onClick={() => void refresh()}
          >
            <RefreshCcw size={15} />
            刷新
          </button>
        </div>
      </header>
      <div className="execution-layout">
        <aside className="execution-history">
          <div className="execution-panel-title">
            <History size={16} />
            <span>采集记录</span>
          </div>
          <div className="execution-history-list">
            {historySlice.length ? (
              historySlice.map((job) => (
                <button
                  key={job.id}
                  type="button"
                  className={`execution-record ${job.id === selected?.id ? "active" : ""}`}
                  onClick={() => setSelectedId(job.id)}
                >
                  <strong>{time(job.createdAt)}</strong>
                  <span className={`execution-state state-${jobDisplayStatus(job)}`}>
                    {statusText(jobDisplayStatus(job))}
                  </span>
                  <small>{duration(job.startedAt, job.finishedAt)}</small>
                </button>
              ))
            ) : (
              <div className="execution-empty">暂无采集记录</div>
            )}
          </div>
          {historyPages > 1 ? (
            <div className="execution-history-pager">
              <button
                type="button"
                disabled={historyPage <= 1}
                onClick={() => setHistoryPage((page) => Math.max(1, page - 1))}
              >
                上一页
              </button>
              <span>
                {historyPage}/{historyPages}
              </span>
              <button
                type="button"
                disabled={historyPage >= historyPages}
                onClick={() =>
                  setHistoryPage((page) => Math.min(historyPages, page + 1))
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
                  <strong>本次采集</strong>
                  <span>{time(selected.createdAt)}</span>
                </div>
                <span className={`execution-state state-${displayStatus}`}>
                  {statusText(displayStatus)}
                </span>
              </div>
              {displayStatus === "failed" ? (
                <div className="execution-error">
                  <AlertTriangle size={17} />
                  <div>
                    <strong>采集任务执行失败</strong>
                    <p>
                      {toUserFailureMessage(selected.errorMessage) ||
                        "请查看失败节点和事件日志。"}
                    </p>
                  </div>
                  <button
                    type="button"
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
              ) : null}
              <div className="execution-pipeline">
                {stages.map(([key, title, description], index) => {
                  const persistedState = state(key);
                  const stageStatus = getHotNewsStageDisplayStatus({
                    persistedState,
                    jobStatus: displayStatus,
                    stageIndex: index,
                    currentStageIndex,
                    isCurrentStage: effectiveCurrentStageKey === key,
                  });
                  const liveStageMessage =
                    resolveRadarStageProgressMessage(attemptEvents, key) ||
                    (displayStatus === "running" &&
                    effectiveCurrentStageKey === key &&
                    selected?.currentStep &&
                    (parent(selected.currentStep) === key ||
                      selected.currentStep === key)
                      ? selected.progressMessage || undefined
                      : undefined);
                  const stageNote =
                    persistedState === "succeeded" || persistedState === "failed"
                      ? note(key)
                      : displayStatus === "failed" && currentStageKey === key
                        ? toUserFailureMessage(selected.errorMessage) || note(key)
                        : displayStatus === "running" &&
                            effectiveCurrentStageKey === key
                          ? liveStageMessage || runningNotes[key]
                          : note(key);
                  const showLiveNote =
                    Boolean(stageNote) || stageStatus === "running";
                  const liveMessage =
                    stageNote ||
                    (stageStatus === "running"
                      ? liveStageMessage ||
                        runningNotes[key] ||
                        `正在执行“${title}”`
                      : "");
                  return (
                    <div
                      className={`execution-step ${stepClassName(stageStatus)}`}
                      key={key}
                    >
                      <div className="execution-node">
                        {stageStatus === "succeeded" ? (
                          <CheckCircle2 size={17} />
                        ) : stageStatus === "failed" ? (
                          <AlertTriangle size={17} />
                        ) : stageStatus === "running" ? (
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
                          <span>{statusText(stageStatus)}</span>
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
                  type="button"
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
                  type="button"
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
              {open.nodes ? (
                <section className="execution-subsection">
                  <div className="execution-subsection-heading">
                    <div>
                      <h4>技术执行明细</h4>
                      <p>
                        用于排查节点重试与输出摘要；上方流程图用于查看整体进度。
                      </p>
                    </div>
                    <span>{attemptNodes.length} 个节点</span>
                  </div>
                  <div className="hot-technical-node-list">
                    {attemptNodes.length ? (
                      attemptNodes.map((node) => (
                        <div key={node.id}>
                          <span
                            className={`execution-state state-${normalizeHotNewsNodeStatus(node)}`}
                          >
                            {statusText(normalizeHotNewsNodeStatus(node))}
                          </span>
                          <strong>
                            {labels[node.nodeKey] ||
                              `内部步骤：${node.nodeKey.replace(/^radar_/, "").replaceAll("_", " ")}`}
                          </strong>
                          <small>
                            第 {node.attemptNo} 次 · {time(node.startedAt)} ·{" "}
                            {duration(node.startedAt, node.finishedAt)}
                          </small>
                          <p>
                            {node.errorMessage
                              ? toUserFailureMessage(node.errorMessage)
                              : `输出：${readableRadarOutput(
                                  node.outputSummary,
                                  "节点已完成，未保存输出摘要",
                                )}`}
                          </p>
                        </div>
                      ))
                    ) : (
                      <p>当前批次还没有节点记录。</p>
                    )}
                  </div>
                </section>
              ) : null}
              {open.events ? (
                <section className="execution-subsection">
                  <div className="execution-subsection-heading">
                    <div>
                      <h4>技术事件日志</h4>
                      <p>
                        保留后台上报的原始事件，主要用于定位异常，不代表额外执行了一套流程。
                      </p>
                    </div>
                    <span>{attemptEvents.length} 条事件</span>
                  </div>
                  <div className="execution-event-list">
                    {attemptEvents.length ? (
                      attemptEvents.map((event) => (
                        <div key={event.id}>
                          <time>{time(event.createdAt)}</time>
                          <b>{eventStepLabel(event.step)}</b>
                          <span>{event.message || "—"}</span>
                        </div>
                      ))
                    ) : (
                      <div>
                        <time>—</time>
                        <b>暂无事件</b>
                        <span>当前批次还没有事件记录。</span>
                      </div>
                    )}
                  </div>
                </section>
              ) : null}
            </>
          ) : (
            <div className="execution-empty">请选择左侧采集记录，或从雷达页开始采集。</div>
          )}
        </div>
      </div>
    </section>
  );
}
