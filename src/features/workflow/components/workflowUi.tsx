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


export type WorkflowTab =
  "overview" | "designer" | "versions" | "runs" | "analytics";

export type CourseOption = {
  id: string;
  title: string;
  series: string;
  lesson: string;
};

export const tabs: Array<{ key: WorkflowTab; label: string; icon: typeof Workflow }> =
  [
    { key: "overview", label: "工作流概览", icon: Workflow },
    { key: "designer", label: "流程设计", icon: GitBranch },
    { key: "versions", label: "版本发布", icon: History },
    { key: "runs", label: "运行记录", icon: Activity },
    { key: "analytics", label: "运行分析", icon: BarChart3 },
  ];

export const runLabels: Record<string, string> = {
  pending: "等待执行",
  running: "执行中",
  succeeded: "已完成",
  failed: "已失败",
  cancelled: "已取消",
};

export function workflowSceneLabel(scene: string | null | undefined): string {
  const code = (scene ?? "").trim().toLowerCase();
  if (code.includes("hot_news") || code.includes("hot-news")) {
    return "热点模式";
  }
  if (code.includes("course")) {
    return "课程模式";
  }
  return scene?.trim() || "未指定场景";
}

export function CompactFlow({ nodes }: { nodes: WorkflowDefinitionNode[] }) {
  return (
    <div className="workflow-compact-flow">
      {nodes.map((node, index) => (
        <div key={node.key}>
          <span>{index + 1}</span>
          <div>
            <b>{node.name}</b>
            <small>{node.stage}</small>
          </div>
          {index < nodes.length - 1 && <ChevronRight size={15} />}
        </div>
      ))}
    </div>
  );
}

export function WorkflowMetric({
  label,
  value,
  note,
}: {
  label: string;
  value: string;
  note: string;
}) {
  return (
    <article className="workflow-metric">
      <span>{label}</span>
      <strong>{value}</strong>
      <small>{note}</small>
    </article>
  );
}

export function ValidationBanner({ report }: { report: WorkflowValidation }) {
  return (
    <div className={`workflow-banner ${report.valid ? "success" : "error"}`}>
      {report.valid ? <CheckCircle2 size={17} /> : <AlertTriangle size={17} />}
      <span>
        {report.valid
          ? `校验通过${report.warnings.length ? `，有 ${report.warnings.length} 项建议` : ""}`
          : `存在 ${report.errors.length} 个阻断问题`}
      </span>
      {!report.valid && (
        <small>{report.errors.map((item) => item.message).join("；")}</small>
      )}
    </div>
  );
}

export function nextVersion(current?: string) {
  if (!current) return "1.0";
  const [major, minor] = current.split(".").map(Number);
  return `${major}.${(minor || 0) + 1}`;
}

export function formatDate(value: string) {
  return new Intl.DateTimeFormat("zh-CN", {
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
    hour12: false,
  }).format(new Date(value));
}

export function formatDuration(seconds: number) {
  if (!seconds) return "0 秒";
  return seconds >= 60
    ? `${Math.floor(seconds / 60)}分${Math.round(seconds % 60)}秒`
    : `${Math.round(seconds)} 秒`;
}
