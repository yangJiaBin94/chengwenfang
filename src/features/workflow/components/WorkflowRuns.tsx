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

import { formatDate, runLabels } from "./workflowUi";

export function WorkflowRuns({
  runs,
  onOpenExecution,
}: {
  runs: WorkflowRun[];
  onOpenExecution: (jobId: string) => void;
}) {
  const [status, setStatus] = useState("all");
  const [version, setVersion] = useState("all");
  const [course, setCourse] = useState("");
  const [failedNode, setFailedNode] = useState("");
  const [date, setDate] = useState("");
  const versionOptions = Array.from(
    new Set(runs.map((run) => run.version ?? "legacy")),
  );
  const visible = runs.filter(
    (run) =>
      (status === "all" || run.status === status) &&
      (version === "all" || (run.version ?? "legacy") === version) &&
      (!course ||
        String(run.stateSummary.courseId ?? "")
          .toLowerCase()
          .includes(course.toLowerCase())) &&
      (!failedNode ||
        String(run.currentNode ?? "")
          .toLowerCase()
          .includes(failedNode.toLowerCase())) &&
      (!date || run.createdAt.slice(0, 10) === date),
  );
  return (
    <div className="workflow-section">
      <div className="workflow-run-toolbar">
        <div>
          <h3>运行记录</h3>
          <p>每次运行绑定准确的工作流版本与 Skill 快照。</p>
        </div>
        <div className="workflow-run-filters">
          <select
            value={status}
            onChange={(event) => setStatus(event.target.value)}
          >
            <option value="all">全部状态</option>
            <option value="running">执行中</option>
            <option value="succeeded">已完成</option>
            <option value="failed">已失败</option>
          </select>
          <select
            value={version}
            onChange={(event) => setVersion(event.target.value)}
          >
            <option value="all">全部版本</option>
            {versionOptions.map((item) => (
              <option value={item} key={item}>
                V{item === "legacy" ? "旧版" : item}
              </option>
            ))}
          </select>
          <input
            value={course}
            onChange={(event) => setCourse(event.target.value)}
            placeholder="课程 ID"
          />
          <input
            value={failedNode}
            onChange={(event) => setFailedNode(event.target.value)}
            placeholder="失败节点"
          />
          <input
            type="date"
            value={date}
            onChange={(event) => setDate(event.target.value)}
          />
        </div>
      </div>
      <div className="workflow-run-table">
        <div className="workflow-run-head">
          <span>运行编号</span>
          <span>版本</span>
          <span>业务对象</span>
          <span>状态</span>
          <span>开始时间</span>
          <span>操作</span>
        </div>
        {visible.map((run) => (
          <div className="workflow-run-row" key={run.id}>
            <code>{run.id.slice(0, 8)}</code>
            <span>V{run.version ?? "旧版"}</span>
            <span>{String(run.stateSummary.courseId ?? "-")}</span>
            <span>
              <i className={`run-state ${run.status}`}>
                {runLabels[run.status] ?? run.status}
              </i>
            </span>
            <span>{formatDate(run.createdAt)}</span>
            <button
              className="table-link"
              onClick={() => onOpenExecution(run.jobId)}
            >
              查看轨迹
              <ChevronRight size={14} />
            </button>
          </div>
        ))}
        {!visible.length && (
          <div className="workflow-empty">暂无符合条件的运行记录</div>
        )}
      </div>
    </div>
  );
}
