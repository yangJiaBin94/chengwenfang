import { unwrapApiResult } from "./http";

/** 后台任务快照。 */
export type JobData = {
  id: string;
  type: string;
  entityType: string;
  entityId: string;
  /** 统一文档 ID（来自 job input；热点公众号轨 / 课程文章）。 */
  documentId?: string | null;
  /** 热点草稿轨文档 ID（来自 job input）。 */
  draftDocumentId?: string | null;
  status: "pending" | "running" | "succeeded" | "failed" | "cancelled";
  progress: number;
  currentStep: string;
  /** 最近一次进度说明（来自 progress 事件）。 */
  progressMessage?: string | null;
  /** 进度元数据：total / completed / current / stepLabel / title 等。 */
  progressMeta?: Record<string, unknown> | null;
  workflowRunId?: string | null;
  output?: Record<string, unknown> | null;
  errorCode?: string | null;
  errorMessage?: string | null;
  retryCount: number;
  createdAt: string;
  startedAt?: string | null;
  finishedAt?: string | null;
};

/** SSE / 历史事件载荷。 */
export type JobEventData = {
  id: number;
  eventType: string;
  step: string;
  progress: number;
  message: string;
  payload?: Record<string, unknown> | null;
  createdAt: string;
};

/** 工作流节点尝试（含重试与失败详情）。 */
export type WorkflowNodeData = {
  id: string;
  nodeKey: string;
  sequenceNo: number;
  attemptNo: number;
  status: "pending" | "running" | "succeeded" | "failed" | string;
  inputSummary?: Record<string, unknown> | null;
  outputSummary?: Record<string, unknown> | null;
  errorCode?: string | null;
  errorMessage?: string | null;
  startedAt?: string | null;
  finishedAt?: string | null;
};

/** 任务关联的文章版本摘要（仅字段与接口约定相关）。 */
export type JobArticleVersionData = {
  id: string;
  articleId: string;
  versionNo: number;
  content: string;
  contentHash: string;
  source: string;
  reason: string;
  createdAt: string;
  workflowRunId?: string | null;
  audit?: { score: number; passed: number; warnings: number; failed: number };
};

const JOB_EVENT_TYPES = [
  "queued",
  "started",
  "progress",
  "warning",
  "error",
  "failed",
  "cancelled",
  "succeeded",
  "completed",
] as const;

/** 是否为失败类事件（用于 SSE 兜底结束）。 */
export function isJobFailureEvent(eventType: string) {
  return eventType === "error" || eventType === "failed" || eventType === "cancelled";
}

/** 按 ID 读取任务快照。 */
export async function loadJob(jobId: string) {
  return unwrapApiResult<JobData>(
    await fetch(`/api/jobs/${encodeURIComponent(jobId)}`, { cache: "no-store" }),
  );
}

/** 回放一次执行的全部持久化事件。 */
export async function loadJobEventHistory(jobId: string) {
  return unwrapApiResult<JobEventData[]>(
    await fetch(`/api/jobs/${encodeURIComponent(jobId)}/event-history`, {
      cache: "no-store",
    }),
  );
}

/** 读取节点尝试列表（运行中任务也可能已有节点行）。 */
export async function loadJobNodes(jobId: string) {
  return unwrapApiResult<WorkflowNodeData[]>(
    await fetch(`/api/jobs/${encodeURIComponent(jobId)}/nodes`, { cache: "no-store" }),
  );
}

/** 读取任务产出的文章版本列表。 */
export async function loadJobArticleVersions(jobId: string) {
  return unwrapApiResult<JobArticleVersionData[]>(
    await fetch(`/api/jobs/${encodeURIComponent(jobId)}/article-versions`),
  );
}

/** 重试失败任务（复用已完成节点产物）。 */
export async function retryFailedJob(jobId: string) {
  return unwrapApiResult<JobData>(
    await fetch(`/api/jobs/${encodeURIComponent(jobId)}/retry-failed`, {
      method: "POST",
    }),
  );
}

/** 订阅 SSE，断线后按 Last-Event-ID 重放；并以短轮询兜底终态。 */
export async function waitForBackgroundJob(
  jobId: string,
  onProgress?: (event: JobEventData) => void,
): Promise<JobData> {
  return new Promise<JobData>((resolve, reject) => {
    const events = new EventSource(`/api/jobs/${encodeURIComponent(jobId)}/events`);
    let settled = false;

    function finish(action: () => void) {
      if (settled) return;
      settled = true;
      window.clearInterval(timer);
      events.close();
      action();
    }

    async function settleFromJob(latest: JobData, fallbackMessage?: string) {
      if (latest.status === "failed" || latest.status === "cancelled") {
        finish(() =>
          reject(new Error(latest.errorMessage || fallbackMessage || "任务执行失败。")),
        );
        return;
      }
      if (latest.status === "succeeded") {
        finish(() => resolve(latest));
      }
    }

    /** 解码一条持久化事件，仅在显式终态事件时结束。 */
    async function handleEvent(raw: Event) {
      if (!(raw instanceof MessageEvent) || settled) return;
      const parsed = JSON.parse(raw.data) as JobEventData & {
        createdAt?: string | number;
      };
      const createdAt =
        typeof parsed.createdAt === "number"
          ? new Date(parsed.createdAt).toISOString()
          : typeof parsed.createdAt === "string" &&
              /^\d{11,13}$/.test(parsed.createdAt.trim())
            ? new Date(Number(parsed.createdAt)).toISOString()
            : String(parsed.createdAt ?? "");
      const event: JobEventData = { ...parsed, createdAt };
      onProgress?.(event);
      try {
        await settleFromJob(await loadJob(jobId), event.message);
      } catch {
        if (isJobFailureEvent(event.eventType)) {
          finish(() => reject(new Error(event.message || "任务执行失败。")));
        }
      }
    }

    async function syncFromApi() {
      if (settled) return;
      try {
        await settleFromJob(await loadJob(jobId));
      } catch {
        // 保持监听；下一次轮询或 SSE 可恢复
      }
    }

    for (const eventType of JOB_EVENT_TYPES) {
      events.addEventListener(eventType, handleEvent);
    }
    events.onmessage = handleEvent;
    events.onerror = () => {
      void syncFromApi();
    };
    const timer = window.setInterval(() => void syncFromApi(), 1_500);
    void syncFromApi();
  });
}
