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
import { formatElapsedDurationLabel } from "../../../shared/format/duration";
import { formatExecutionNodeTime } from "../../../shared/format/time";
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
  wordCountLabel,
  imageCountLabel,
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
} from "../components/hotNewsExecutionHelpers";

export { buildHotNewsExecutionAttemptBatches, getHotNewsStageDisplayStatus, normalizeHotNewsNodeStatus, resolveHotNewsImageProgressMessage, resolveHotNewsWorkflowRunId } from "../components/hotNewsExecutionHelpers";
import { HotNewsExecutionView } from "../components/HotNewsExecutionView";


/** 热点执行页节点行（与任务节点同形）。 */

/** 与课程执行轨迹页一致的状态文案。 */

/** 步骤节点 class，与课程模式 execution-step 状态修饰符一致。 */


export function HotNewsExecutionPage({
  articleId,
  navigate,
}: {
  articleId: string;
  navigate: (path: string) => void;
}) {
  const [article, setArticle] = useState<HotNewsArticle | null>(null);
  const [skeleton, setSkeleton] = useState<HotNewsArticleSkeleton | null>(null);
  const [jobs, setJobs] = useState<JobData[]>([]);
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [nodes, setNodes] = useState<HotNewsWorkflowNode[]>([]);
  const [events, setEvents] = useState<JobEventData[]>([]);
  const [historyPage, setHistoryPage] = useState(1);
  const HISTORY_PAGE_SIZE = 10;
  const [open, setOpen] = useState({
    skills: false,
    nodes: false,
    events: false,
  });
  const [selectedAttemptNumber, setSelectedAttemptNumber] = useState<
    number | null
  >(null);
  const [loading, setLoading] = useState(true);
  /** 跨轮询保留 documentId，避免 interval 闭包丢失 */
  const documentIdRef = useRef<string | null>(null);
  const refresh = async () => {
    // 深链首次：从 ?documentId= / ?job= 解析；之后用 ref
    const seedDocumentId = await resolveHotNewsDocumentIdFromLocation(
      documentIdRef.current || article?.documentId,
    );
    const a = await getHotNewsArticle(articleId, seedDocumentId);
    if (a.documentId) {
      documentIdRef.current = a.documentId;
    }
    const [j, skeletonView] = await Promise.all([
      listHotNewsArticleGenerationRuns(articleId, a.documentId),
      getHotNewsArticleSkeleton(a.topicId).catch(() => null),
    ]);
    setArticle(a);
    setJobs(j);
    setSkeleton(skeletonView);
    const jobFromQuery = new URLSearchParams(window.location.search).get("job");
    setSelectedId((current) => {
      if (current && j.some((row) => row.id === current)) {
        return current;
      }
      if (jobFromQuery && j.some((row) => row.id === jobFromQuery)) {
        return jobFromQuery;
      }
      return j[0]?.id || null;
    });
  };
  useEffect(() => {
    void refresh().finally(() => setLoading(false));
  }, [articleId]);
  usePolling(() => refresh(), 3000, [articleId]);
  const selected = jobs.find((x) => x.id === selectedId) || null;
  useEffect(() => {
    setNodes([]);
    setEvents([]);
    if (!selected) return;
    // 按 jobId 拉节点：运行中尚无 output.workflowRunId，也能看到真实进度
    void Promise.all([
      loadJobNodes(selected.id),
      loadJobEventHistory(selected.id),
    ]).then(([n, e]) => {
      setNodes(n);
      setEvents(e);
    });
  }, [
    selectedId,
    selected?.status,
    selected?.currentStep,
    selected?.progress,
  ]);
  const attemptBatches = useMemo(
    () => buildHotNewsExecutionAttemptBatches(events, nodes),
    [events, nodes],
  );
  const selectedAttempt =
    attemptBatches.find((batch) => batch.number === selectedAttemptNumber) ||
    attemptBatches[attemptBatches.length - 1] || {
      number: 1,
      events: [],
      nodes: [],
      startedAt: null,
      finishedAt: null,
    };
  const attemptEvents = selectedAttempt.events;
  const attemptNodes = selectedAttempt.nodes;
  useEffect(() => setSelectedAttemptNumber(null), [selectedId]);
  const latestAttemptEvent = attemptEvents[attemptEvents.length - 1] || null;
  const latestAttemptStep = latestAttemptEvent?.step || selected?.currentStep || "";
  const viewingLatestAttempt =
    selectedAttempt.number ===
    (attemptBatches[attemptBatches.length - 1]?.number ?? selectedAttempt.number);
  const visibleJobs = jobs;
  const historyPageCount = Math.max(
    1,
    Math.ceil(visibleJobs.length / HISTORY_PAGE_SIZE),
  );
  const safeHistoryPage = Math.min(historyPage, historyPageCount);
  const pagedJobs = visibleJobs.slice(
    (safeHistoryPage - 1) * HISTORY_PAGE_SIZE,
    safeHistoryPage * HISTORY_PAGE_SIZE,
  );
  useEffect(() => {
    if (!selectedId) return;
    const index = jobs.findIndex((job) => job.id === selectedId);
    if (index < 0) return;
    setHistoryPage(Math.floor(index / HISTORY_PAGE_SIZE) + 1);
  }, [selectedId, jobs]);
  const goBackToWorkspace = async () => {
    const seedDocumentId = await resolveHotNewsDocumentIdFromLocation(
      documentIdRef.current || article?.documentId,
    );
    const current =
      article || (await getHotNewsArticle(articleId, seedDocumentId));
    navigate(
      withDocumentIdQuery(
        `/modes/hot-news/topics/${encodeURIComponent(current.topicId)}/articles/${encodeURIComponent(articleId)}`,
        current.documentId || seedDocumentId,
      ),
    );
  };
  const skills = useMemo(
    () =>
      [
        ...attemptEvents
        .flatMap((e) =>
          Array.isArray(e.payload?.skills) ? e.payload.skills : [],
        ),
        ...attemptNodes.flatMap((n) =>
          Array.isArray(n.outputSummary?.skills) ? n.outputSummary.skills : [],
        ),
      ]
        .filter((x) => !!x && typeof x === "object") as Array<
          Record<string, unknown>
        >,
    [attemptEvents, attemptNodes],
  );
  const state = (key: string) => {
    const latestByNodeKey = new Map<string, HotNewsWorkflowNode>();
    attemptNodes
      .filter((n) => parent(n.nodeKey) === key)
      .forEach((node) => {
        const previous = latestByNodeKey.get(node.nodeKey);
        if (!previous || node.attemptNo >= previous.attemptNo) {
          latestByNodeKey.set(node.nodeKey, node);
        }
      });
    const ns = [...latestByNodeKey.values()];
    const normalizedStates = ns.map(normalizeHotNewsNodeStatus);
    const persistedState = !ns.length
      ? "pending"
      : normalizedStates.some((status) => status === "failed")
        ? "failed"
        : normalizedStates.some((status) => status === "running")
          ? "running"
          : normalizedStates.every((status) => status === "succeeded")
            ? "succeeded"
            : "pending";
    // 仅最新批次跟 Job 重试态；历史批次只看该批节点结果，避免失败阶段被标成「执行中」
    const retrying =
      viewingLatestAttempt &&
      (Boolean(
        latestAttemptEvent &&
          (latestAttemptStep.includes("retry") ||
            latestAttemptEvent.message.includes("重试") ||
            latestAttemptEvent.message.includes("重新排队")),
      ) ||
        (selected?.status === "pending" &&
          (selected.currentStep === "retry_queued" ||
            selected.currentStep === "recovered")));
    if (retrying) {
      const failedNode = ns.find((node) => normalizeHotNewsNodeStatus(node) === "failed");
      if (failedNode) {
        const failedStage = parent(failedNode.nodeKey);
        if (key === failedStage) return "running";
        const failedIndex = stages.findIndex(([stageKey]) => stageKey === failedStage);
        const stageIndex = stages.findIndex(([stageKey]) => stageKey === key);
        if (failedIndex >= 0 && stageIndex > failedIndex) return "pending";
      }
    }
    const failedStageIndex = stages.findIndex(([stageKey]) =>
      ns.some((node) => normalizeHotNewsNodeStatus(node) === "failed" && parent(node.nodeKey) === stageKey),
    );
    if (selected?.status === "failed" && failedStageIndex >= 0) {
      const stageIndex = stages.findIndex(([stageKey]) => stageKey === key);
      if (stageIndex > failedStageIndex) return "pending";
    }
    // A node can be left as running when an upstream request aborts abruptly.
    // The job terminal state is authoritative, so a failed run must never spin forever.
    if (selected?.status === "failed" && persistedState === "running") {
      return parent(selected.currentStep) === key ? "failed" : "pending";
    }
    return persistedState;
  };
  // The job row can lag behind durable node attempts during the final callback.
  // Use the node record to keep the page from showing a completed run as active.
  const nodeDisplayStatus = stages.map(([key]) => state(key));
  const isRetrying =
    viewingLatestAttempt &&
    selected?.status === "pending" &&
    (selected.currentStep === "retry_queued" || selected.currentStep === "recovered");
  // 浏览历史批次时，流程图只反映该批次节点结果，不跟当前 Job 实时状态串台
  const displayStatus: JobData["status"] = !viewingLatestAttempt
    ? nodeDisplayStatus.some((status) => status === "failed")
      ? "failed"
      : nodeDisplayStatus.length > 0 &&
          nodeDisplayStatus.every((status) => status === "succeeded")
        ? "succeeded"
        : "failed"
    : isRetrying
      ? "running"
      : nodeDisplayStatus.some((status) => status === "failed")
        ? "failed"
        : nodeDisplayStatus.every((status) => status === "succeeded")
          ? "succeeded"
          : selected?.status === "pending" &&
              (selected.currentStep === "retry_queued" ||
                selected.currentStep === "recovered")
            ? "running"
            : selected?.status || "pending";
  const currentStageKey = selected ? parent(latestAttemptStep) || parent(selected.currentStep) : null;
  const effectiveCurrentStageKey = (() => {
    if (!selected || displayStatus !== "running" || !viewingLatestAttempt) return null;
    const runningStage = stages.find(([key]) => state(key) === "running")?.[0];
    if (runningStage) return runningStage;
    const pendingStage = stages.find(([key]) => state(key) === "pending")?.[0];
    return pendingStage || currentStageKey;
  })();
  const currentStageIndex = effectiveCurrentStageKey
    ? stages.findIndex(([key]) => key === effectiveCurrentStageKey)
    : -1;
  const latestChildNode = (key: string) =>
    attemptNodes
      .filter((x) => x.nodeKey !== key && parent(x.nodeKey) === key)
      .reduce<HotNewsWorkflowNode | null>(
        (latest, item) =>
          !latest ||
          new Date(item.startedAt || 0).getTime() >
            new Date(latest.startedAt || 0).getTime()
            ? item
            : latest,
        null,
      );
  const childNodeNote = (node: HotNewsWorkflowNode | null) => {
    if (!node) return "";
    const summary = node.outputSummary || node.inputSummary || {};
    const title =
      typeof summary.title === "string"
        ? summary.title
        : typeof summary.sectionKey === "string"
          ? String(summary.sectionKey)
          : "";
    const index =
      typeof summary.sectionIndex === "number"
        ? summary.sectionIndex
        : typeof summary.chunkIndex === "number"
          ? summary.chunkIndex
          : null;
    const total =
      typeof summary.sectionTotal === "number"
        ? summary.sectionTotal
        : typeof summary.chunkCount === "number"
          ? summary.chunkCount
          : null;
    const prefix =
      index && total ? `第 ${index}/${total} 段` : "当前分段";
    const chars =
      typeof node.outputSummary?.chars === "number"
        ? `，长度 ${node.outputSummary.chars} 个字符`
        : "";
    return `${prefix}${title ? `：${title}` : ""}${chars}`;
  };
  const note = (key: string) => {
    const failed = attemptNodes
      .filter((x) => parent(x.nodeKey) === key && normalizeHotNewsNodeStatus(x) === "failed")
      .reduce<HotNewsWorkflowNode | null>(
        (latest, item) =>
          !latest || item.attemptNo >= latest.attemptNo ? item : latest,
        null,
      );
    if (failed?.errorMessage) {
      return key === "hot_news_generate_images"
        ? toImageStepFailureMessage(
            failed.errorMessage,
            resolveHotNewsImageProgressMessage(attemptEvents),
          )
        : toUserFailureMessage(failed.errorMessage);
    }
    const child = latestChildNode(key);
    if (key === "hot_news_generate_draft" && child) {
      return childNodeNote(child);
    }
    if (key === "hot_news_generate_wechat" && child) {
      return childNodeNote(child);
    }
    const n = attemptNodes
      .filter((x) => x.nodeKey === key)
      .reduce<HotNewsWorkflowNode | null>(
        (latest, item) =>
          !latest || item.attemptNo >= latest.attemptNo ? item : latest,
        null,
      );
    if (key === "hot_news_generate_draft" && n) {
      const chars = n.outputSummary?.chars;
      return typeof chars === "number"
        ? `完整原稿长度 ${chars} 个字符`
        : "完整原稿已生成";
    }
    if (key === "hot_news_generate_wechat" && n) {
      const chars = n.outputSummary?.chars;
      return typeof chars === "number"
        ? `公众号文章长度 ${chars} 个字符${selected?.status === "succeeded" && article?.wordCount ? `，正文字数 ${article.wordCount} 字` : ""}`
        : "公众号文章已生成";
    }
    if (n?.errorMessage) {
      return key === "hot_news_generate_images"
        ? toImageStepFailureMessage(
            n.errorMessage,
            resolveHotNewsImageProgressMessage(attemptEvents),
          )
        : toUserFailureMessage(n.errorMessage);
    }
    return n ? readableOutput(n.outputSummary, "该阶段已完成") : "";
  };
  return (
    <HotNewsExecutionView
      HISTORY_PAGE_SIZE={HISTORY_PAGE_SIZE}
      article={article}
      attemptBatches={attemptBatches}
      attemptEvents={attemptEvents}
      attemptNodes={attemptNodes}
      currentStageIndex={currentStageIndex}
      currentStageKey={currentStageKey}
      effectiveCurrentStageKey={effectiveCurrentStageKey}
      events={events}
      goBackToWorkspace={goBackToWorkspace}
      historyPageCount={historyPageCount}
      loading={loading}
      nodes={nodes}
      note={note}
      open={open}
      pagedJobs={pagedJobs}
      refresh={refresh}
      safeHistoryPage={safeHistoryPage}
      selected={selected}
      selectedAttempt={selectedAttempt}
      selectedId={selectedId}
      setHistoryPage={setHistoryPage}
      setOpen={setOpen}
      setSelectedAttemptNumber={setSelectedAttemptNumber}
      setSelectedId={setSelectedId}
      skeleton={skeleton}
      skills={skills}
      state={state}
      visibleJobs={visibleJobs}
    />
  );
}
