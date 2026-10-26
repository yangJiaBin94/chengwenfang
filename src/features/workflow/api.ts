import { unwrapApiResult } from "../../shared/api/http";
import { CUSTOM_AI_TYPE_KEY, isCustomAiNode } from "./model";

export { CUSTOM_AI_TYPE_KEY, isCustomAiNode };

export type WorkflowNodeConfig = {
  timeoutSeconds: number;
  maxAttempts: number;
  failurePolicy: "stop" | "retry_then_stop" | "continue_with_warning";
  /** 自定义节点提示词；仅 custom_ai 使用。 */
  prompt?: string;
  [key: string]: unknown;
};

export type WorkflowSkillBinding = {
  skillId: string;
  name: string;
  priority: number;
  enabled: boolean;
};

export type WorkflowDefinitionNode = {
  key: string;
  typeKey: string;
  name: string;
  description: string;
  stage: string;
  sequenceNo: number;
  enabled: boolean;
  config: WorkflowNodeConfig;
  position: { x: number; y: number };
  skillBindings: WorkflowSkillBinding[];
};

export type WorkflowDefinitionEdge = {
  id: string;
  source: string;
  sourcePort: string;
  target: string;
  targetPort: string;
  condition?: { field: string; operator: string; value: unknown };
  loop?: boolean;
  maxIterations?: number;
};

export type WorkflowDefinitionDataView = {
  schemaVersion: string;
  name: string;
  description: string;
  scene: string;
  modeId: string;
  nodes: WorkflowDefinitionNode[];
  edges: WorkflowDefinitionEdge[];
};

export type WorkflowValidation = {
  valid: boolean;
  errors: Array<{ code: string; message: string; nodeKey: string }>;
  warnings: Array<{ code: string; message: string; nodeKey: string }>;
  checkedAt: string;
  definitionHash: string;
};

export type WorkflowSummary = {
  id: string;
  key: string;
  name: string;
  description: string;
  scene: string;
  status: string;
  owner: string;
  publishedVersionId: string | null;
  publishedVersion: string | null;
  draftRevision: number | null;
  updatedAt: string;
};

export type WorkflowDetail = WorkflowSummary & {
  definition: WorkflowDefinitionDataView;
  validation: WorkflowValidation;
  metrics: WorkflowMetrics;
};

export type WorkflowDraft = {
  id: string;
  workflowId: string;
  baseVersionId: string | null;
  revision: number;
  definition: WorkflowDefinitionDataView;
  validation: WorkflowValidation | null;
  updatedAt: string;
};

export type WorkflowVersion = {
  id: string;
  workflowId: string;
  version: string;
  revision: number;
  snapshot: WorkflowDefinitionDataView;
  validation: WorkflowValidation;
  releaseNotes: string;
  publishedBy: string;
  createdAt: string;
};

export type WorkflowRun = {
  id: string;
  jobId: string;
  workflowVersionId: string | null;
  version: string | null;
  triggerSource: string | null;
  status: string;
  currentNode: string | null;
  stateSummary: Record<string, unknown>;
  costSummary: Record<string, unknown> | null;
  createdAt: string;
  finishedAt: string | null;
};

export type WorkflowMetrics = {
  totalRuns: number;
  successRate: number;
  averageDurationSeconds: number;
  p95DurationSeconds: number;
  failedNodes: Array<{ nodeKey: string; count: number }>;
};

export type WorkflowNodeMetric = {
  nodeKey: string;
  attempts: number;
  failures: number;
  averageDurationMs: number;
};
export type WorkflowVersionMetric = {
  version: string;
  runs: number;
  successRate: number;
  p95DurationSeconds: number;
  totalTokens: number;
};
export type WorkflowAlert = {
  id: string;
  workflowId: string;
  runId: string | null;
  metric: string;
  severity: string;
  title: string;
  message: string;
  threshold: Record<string, unknown>;
  status: string;
  createdAt: string;
  resolvedAt: string | null;
};

export type WorkflowNodeType = {
  key: string;
  name: string;
  description: string;
  category: string;
  modelType: "none" | "text" | "image";
  executorVersion: string;
  configurability: "A" | "B" | "C" | "D";
  supportsRetry: boolean;
  sideEffects: boolean;
  includeInBaseline: boolean;
  configSchema: Record<string, unknown>;
  inputSchema: Record<string, unknown>;
  outputSchema: Record<string, unknown>;
};

async function payload<T>(response: Response): Promise<T> {
  return unwrapApiResult<T>(response);
}

/** 功能按钮 ↔ 工作流绑定键（与后端 WorkflowFeatureBinding 对齐）。 */
export const WorkflowFeatureKey = {
  HOT_NEWS_RADAR_COLLECT: "hot_news_radar_collect",
  HOT_NEWS_ARTICLE_GENERATION: "hot_news_article_generation",
  COURSE_CONTENT_GENERATION: "course_content_generation",
} as const;

export type WorkflowFeatureKeyValue =
  (typeof WorkflowFeatureKey)[keyof typeof WorkflowFeatureKey];

/** 功能键对应工作流可用性。 */
export type WorkflowFeatureAvailability = {
  featureKey: string;
  available: boolean;
  workflowId?: string | null;
  name?: string | null;
  status?: string | null;
  publishedVersionId?: string | null;
  message?: string | null;
};

/**
 * 反查功能绑定工作流是否可用（按钮点击门禁）。
 *
 * @param featureKey 功能键
 */
export async function getWorkflowFeatureAvailability(
  featureKey: WorkflowFeatureKeyValue | string,
) {
  return payload<WorkflowFeatureAvailability>(
    await fetch(
      `/api/workflows/features/${encodeURIComponent(featureKey)}`,
      { cache: "no-store" },
    ),
  );
}

/**
 * 若功能无可用工作流则抛错，供按钮启动前拦截。
 *
 * @param featureKey 功能键
 * @return 可用性结果（available=true）
 */
export async function assertWorkflowFeatureAvailable(
  featureKey: WorkflowFeatureKeyValue | string,
) {
  const availability = await getWorkflowFeatureAvailability(featureKey);
  if (!availability.available) {
    throw new Error(
      availability.message ||
        "相关工作流已停用或未发布，请先在工作流中心启用。",
    );
  }
  return availability;
}

export async function listWorkflows() {
  return payload<WorkflowSummary[]>(
    await fetch("/api/workflows", { cache: "no-store" }),
  );
}

export async function listWorkflowNodeTypes() {
  return payload<WorkflowNodeType[]>(
    await fetch("/api/workflows/node-types", { cache: "no-store" }),
  );
}

export async function loadWorkflow(id: string) {
  return payload<WorkflowDetail>(
    await fetch(`/api/workflows/${id}`, { cache: "no-store" }),
  );
}

export async function loadWorkflowDraft(id: string) {
  return payload<WorkflowDraft>(
    await fetch(`/api/workflows/${id}/draft`, { cache: "no-store" }),
  );
}

export async function saveWorkflowDraft(id: string, draft: WorkflowDraft) {
  return payload<WorkflowDraft>(
    await fetch(`/api/workflows/${id}/draft`, {
      method: "PUT",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        revision: draft.revision,
        definition: draft.definition,
      }),
    }),
  );
}

export async function validateWorkflow(
  id: string,
  definition: WorkflowDefinitionDataView,
) {
  return payload<WorkflowValidation>(
    await fetch(`/api/workflows/${id}/draft/validate`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ definition }),
    }),
  );
}

export async function resetWorkflowDraft(id: string, draft: WorkflowDraft) {
  return payload<WorkflowDraft>(await fetch(`/api/workflows/${id}/draft/reset`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ revision: draft.revision, definition: draft.definition }),
  }));
}

export async function loadWorkflowDiff(id: string) {
  return payload<Record<string, unknown>>(
    await fetch(`/api/workflows/${id}/draft/diff`, { cache: "no-store" }),
  );
}

export async function publishWorkflow(
  id: string,
  version: string,
  releaseNotes: string,
) {
  return payload<WorkflowVersion>(
    await fetch(`/api/workflows/${id}/publish`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ version, releaseNotes }),
    }),
  );
}

export async function listWorkflowVersions(id: string) {
  return payload<WorkflowVersion[]>(
    await fetch(`/api/workflows/${id}/versions`, { cache: "no-store" }),
  );
}

export async function restoreWorkflowVersion(
  workflowId: string,
  versionId: string,
) {
  return payload<WorkflowDraft>(
    await fetch(
      `/api/workflows/${workflowId}/versions/${versionId}/create-draft`,
      { method: "POST" },
    ),
  );
}

export async function listWorkflowRuns(id: string) {
  return payload<WorkflowRun[]>(
    await fetch(`/api/workflows/${id}/runs`, { cache: "no-store" }),
  );
}

export async function loadWorkflowMetrics(id: string) {
  return payload<WorkflowMetrics>(
    await fetch(`/api/workflows/${id}/metrics/summary`, { cache: "no-store" }),
  );
}

export async function createWorkflow(name: string, description: string) {
  return payload<WorkflowSummary>(
    await fetch("/api/workflows", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        name,
        description,
        scene: "course_content_generation",
      }),
    }),
  );
}

export async function updateWorkflow(
  id: string,
  data: { name?: string; description?: string; status?: string },
) {
  return payload<WorkflowSummary>(
    await fetch(`/api/workflows/${id}`, {
      method: "PATCH",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(data),
    }),
  );
}

export async function loadWorkflowVersionDiff(
  id: string,
  from: string,
  to: string,
) {
  return payload<Record<string, unknown>>(
    await fetch(
      `/api/workflows/${id}/versions/diff?from=${encodeURIComponent(from)}&to=${encodeURIComponent(to)}`,
      { cache: "no-store" },
    ),
  );
}

export async function loadWorkflowNodeMetrics(id: string) {
  return payload<WorkflowNodeMetric[]>(
    await fetch(`/api/workflows/${id}/metrics/nodes`, { cache: "no-store" }),
  );
}

export async function loadWorkflowVersionMetrics(id: string) {
  return payload<WorkflowVersionMetric[]>(
    await fetch(`/api/workflows/${id}/metrics/versions`, { cache: "no-store" }),
  );
}

export async function listWorkflowAlerts(id: string) {
  return payload<WorkflowAlert[]>(
    await fetch(`/api/workflows/${id}/alerts`, { cache: "no-store" }),
  );
}

export async function resolveWorkflowAlert(
  workflowId: string,
  alertId: string,
) {
  return payload<WorkflowAlert>(
    await fetch(`/api/workflows/${workflowId}/alerts/${alertId}/resolve`, {
      method: "POST",
    }),
  );
}
