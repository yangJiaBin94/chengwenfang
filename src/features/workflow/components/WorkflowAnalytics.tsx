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

import { WorkflowMetric, formatDate, formatDuration } from "./workflowUi";

export function WorkflowAnalytics({
  metrics,
  runs,
  nodeMetrics,
  versionMetrics,
  alerts,
  onResolveAlert,
  onOpenExecution,
}: {
  metrics: WorkflowMetrics;
  runs: WorkflowRun[];
  nodeMetrics: WorkflowNodeMetric[];
  versionMetrics: WorkflowVersionMetric[];
  alerts: WorkflowAlert[];
  onResolveAlert: (alert: WorkflowAlert) => Promise<void>;
  onOpenExecution: (jobId: string) => void;
}) {
  return (
    <div className="workflow-section">
      <div className="workflow-metric-grid">
        <WorkflowMetric
          label="成功率"
          value={`${metrics.successRate}%`}
          note={`${metrics.totalRuns} 次可靠样本`}
        />
        <WorkflowMetric
          label="平均耗时"
          value={formatDuration(metrics.averageDurationSeconds)}
          note="口径：已结束运行"
        />
        <WorkflowMetric
          label="P95 耗时"
          value={formatDuration(metrics.p95DurationSeconds)}
          note="95% 运行不超过此值"
        />
        <WorkflowMetric
          label="开放告警"
          value={String(alerts.filter((item) => item.status === "open").length)}
          note="按确定性阈值检测"
        />
      </div>
      <div className="workflow-analytics-grid">
        <article className="workflow-panel">
          <header>
            <div>
              <h3>版本指标对比</h3>
              <p>成功率、P95、Token 与样本量使用后端统一口径。</p>
            </div>
            <BarChart3 size={18} />
          </header>
          <div className="workflow-version-metrics">
            {versionMetrics.length ? (
              versionMetrics.map((item) => (
                <div key={item.version}>
                  <strong>
                    {item.version === "legacy" ? "旧版" : `V${item.version}`}
                  </strong>
                  <span>成功率 {item.successRate}%</span>
                  <span>P95 {formatDuration(item.p95DurationSeconds)}</span>
                  <span>{item.totalTokens.toLocaleString()} Token</span>
                  <small>{item.runs} 个样本</small>
                </div>
              ))
            ) : (
              <div className="workflow-empty">暂无版本指标</div>
            )}
          </div>
        </article>
        <article className="workflow-panel">
          <header>
            <div>
              <h3>节点指标下钻</h3>
              <p>查看调用次数、失败次数和平均耗时。</p>
            </div>
            <Activity size={18} />
          </header>
          <div className="workflow-node-metrics">
            {nodeMetrics.length ? (
              nodeMetrics.map((item) => (
                <div key={item.nodeKey}>
                  <b>{item.nodeKey}</b>
                  <span>{item.attempts} 次调用</span>
                  <strong>{item.failures} 次失败</strong>
                  <small>{Math.round(item.averageDurationMs)} ms</small>
                </div>
              ))
            ) : (
              <div className="workflow-empty">暂无节点执行样本</div>
            )}
          </div>
        </article>
      </div>
      <div className="workflow-alert-section">
        <header>
          <div>
            <h3>站内告警</h3>
            <p>阈值、触发指标与关联运行均可追溯。</p>
          </div>
          <Bell size={18} />
        </header>
        {alerts.length ? (
          alerts.map((alert) => (
            <article className={alert.status} key={alert.id}>
              <span>
                <AlertTriangle size={16} />
              </span>
              <div>
                <strong>{alert.title}</strong>
                <p>{alert.message}</p>
                <small>
                  {formatDate(alert.createdAt)} · 指标 {alert.metric}
                </small>
              </div>
              {alert.runId && (
                <button
                  className="table-link"
                onClick={() => {
                  const run = runs.find((item) => item.id === alert.runId);
                  if (run) onOpenExecution(run.jobId);
                }}
                >
                  关联运行
                </button>
              )}
              <button
                disabled={alert.status === "resolved"}
                onClick={() => void onResolveAlert(alert)}
              >
                {alert.status === "resolved" ? "已处理" : "标记处理"}
              </button>
            </article>
          ))
        ) : (
          <div className="workflow-empty">
            <CheckCircle2 size={20} />
            暂无告警
          </div>
        )}
      </div>
    </div>
  );
}
