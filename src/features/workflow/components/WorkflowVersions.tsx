import { useEffect, useMemo, useState } from "react";
import {
  Activity,
  AlertTriangle,
  ArrowLeft,
  BarChart3,
  CheckCircle2,
  ChevronRight,
  Clock3,
  Bell,
  Plus,
  Power,
  Redo2,
  Undo2,
  GitBranch,
  History,
  RefreshCcw,
  Rocket,
  Save,
  Search,
  Settings2,
  ShieldCheck,
  Trash2,
  Workflow,
  X,
} from "lucide-react";
import { listSkills } from "../../settings/api";
import type { Skill } from "../../settings/api";
import {
  createWorkflow,
  listWorkflowRuns,
  listWorkflowNodeTypes,
  listWorkflowAlerts,
  listWorkflowVersions,
  listWorkflows,
  loadWorkflow,
  loadWorkflowDiff,
  loadWorkflowNodeMetrics,
  loadWorkflowVersionDiff,
  loadWorkflowVersionMetrics,
  loadWorkflowDraft,
  publishWorkflow,
  restoreWorkflowVersion,
  saveWorkflowDraft,
  validateWorkflow,
  updateWorkflow,
  resolveWorkflowAlert,
  resetWorkflowDraft,
  CUSTOM_AI_TYPE_KEY,
  isCustomAiNode,
} from "../api";
import type {
  WorkflowDefinitionNode,
  WorkflowDefinitionEdge,
  WorkflowDetail,
  WorkflowDraft,
  WorkflowMetrics,
  WorkflowNodeType,
  WorkflowRun,
  WorkflowSummary,
  WorkflowValidation,
  WorkflowVersion,
  WorkflowAlert,
  WorkflowNodeMetric,
  WorkflowVersionMetric,
} from "../api";

import { nextVersion, CompactFlow, formatDate } from "./workflowUi";

export function WorkflowVersions({
  versions,
  draft,
  currentId,
  onPublish,
  onRestore,
}: {
  versions: WorkflowVersion[];
  draft: WorkflowDraft;
  currentId: string | null;
  onPublish: (version: string, notes: string) => Promise<void>;
  onRestore: (version: WorkflowVersion) => Promise<void>;
}) {
  const [version, setVersion] = useState(nextVersion(versions[0]?.version));
  const [notes, setNotes] = useState("");
  const [diff, setDiff] = useState<Record<string, unknown> | null>(null);
  const [compareFrom, setCompareFrom] = useState(
    versions[1]?.id ?? versions[0]?.id ?? "",
  );
  const [compareTo, setCompareTo] = useState(versions[0]?.id ?? "");
  const [versionDiff, setVersionDiff] = useState<Record<
    string,
    unknown
  > | null>(null);
  useEffect(() => {
    if (!currentId) { setDiff(null); return; }
    void loadWorkflowDiff(draft.workflowId).then(setDiff).catch(() => setDiff(null));
  }, [draft, currentId]);
  const describeDiff = (data: Record<string, unknown> | null) =>
    data
      ? `新增节点 ${((data.addedNodes as string[]) ?? []).length}，删除节点 ${((data.removedNodes as string[]) ?? []).length}，修改节点 ${((data.changedNodes as string[]) ?? []).length}，新增连接 ${Number(data.addedEdges ?? 0)}，删除连接 ${Number(data.removedEdges ?? 0)}`
      : "尚未比较";
  return (
    <div className="workflow-section workflow-version-layout">
      <article className="workflow-panel workflow-release-box">
        <header>
          <div>
            <h3>发布当前草稿</h3>
            <p>发布版本不可修改，只影响发布后创建的任务。</p>
          </div>
          <Rocket size={20} />
        </header>
        <div className="workflow-release-fields">
          <label>
            版本号
            <input
              value={version}
              onChange={(event) => setVersion(event.target.value)}
            />
          </label>
          <label>
            发布说明
            <textarea
              value={notes}
              onChange={(event) => setNotes(event.target.value)}
              placeholder="说明本次配置变化与影响"
            />
          </label>
        </div>
        <div className="workflow-diff-summary">
          <b>草稿差异</b>
          <span>
            {!currentId ? "当前草稿将作为首个发布版本" : diff?.changed ? describeDiff(diff) : "当前草稿与发布版一致"}
          </span>
          {Boolean(diff?.changed) && (
            <small>
              {[
                ...((diff?.addedNodes as string[]) ?? []),
                ...((diff?.removedNodes as string[]) ?? []),
                ...((diff?.changedNodes as string[]) ?? []),
              ].join("、") || "仅连接发生变化"}
            </small>
          )}
        </div>
        <button
          className="primary-button"
          onClick={() => void onPublish(version, notes)}
        >
          <Rocket size={15} />
          发布 V{version}
        </button>
      </article>
      <div className="workflow-version-list">
        <div className="workflow-version-compare">
          <div>
            <h3>发布历史</h3>
            <p>选择两个不可变版本查看结构差异。</p>
          </div>
          <div className="workflow-compare-controls">
            <label>
              <small>基准版本</small>
              <select
                aria-label="基准版本"
                value={compareFrom}
                onChange={(event) => setCompareFrom(event.target.value)}
              >
                {versions.map((item) => (
                  <option value={item.id} key={item.id}>
                    V{item.version}
                  </option>
                ))}
              </select>
            </label>
            <ChevronRight className="workflow-compare-arrow" size={16} />
            <label>
              <small>目标版本</small>
              <select
                aria-label="目标版本"
                value={compareTo}
                onChange={(event) => setCompareTo(event.target.value)}
              >
                {versions.map((item) => (
                  <option value={item.id} key={item.id}>
                    V{item.version}
                  </option>
                ))}
              </select>
            </label>
            <button
              className="ghost-button"
              disabled={!compareFrom || !compareTo || compareFrom === compareTo}
              onClick={async () =>
                setVersionDiff(
                  await loadWorkflowVersionDiff(
                    draft.workflowId,
                    compareFrom,
                    compareTo,
                  ),
                )
              }
            >
              查看差异
            </button>
          </div>
        </div>
        {versionDiff && (
          <div className="workflow-banner">
            <GitBranch size={16} />
            <span>{describeDiff(versionDiff)}</span>
          </div>
        )}
        {versions.map((item) => (
          <article
            className={`workflow-version-card ${item.id === currentId ? "current" : ""}`}
            key={item.id}
          >
            <div className="workflow-version-mark">
              <GitBranch size={16} />
            </div>
            <div>
              <div className="workflow-version-title">
                <strong>V{item.version}</strong>
                {item.id === currentId && <span>当前版本</span>}
              </div>
              <p>{item.releaseNotes || "未填写发布说明"}</p>
              <small>
                {formatDate(item.createdAt)} · {item.publishedBy} ·{" "}
                {item.snapshot.nodes.length} 个节点
              </small>
            </div>
            <button
              disabled={item.id === currentId}
              onClick={() => void onRestore(item)}
            >
              恢复为草稿
            </button>
          </article>
        ))}
      </div>
    </div>
  );
}
