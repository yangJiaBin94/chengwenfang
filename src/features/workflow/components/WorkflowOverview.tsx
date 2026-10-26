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

import {
  workflowSceneLabel,
  type WorkflowTab,
  CompactFlow,
  formatDate,
  WorkflowMetric,
} from "./workflowUi";

export function WorkflowOverview({
  detail,
  draft,
  runs,
  onTab,
}: {
  detail: WorkflowDetail;
  draft: WorkflowDraft;
  runs: WorkflowRun[];
  onTab: (tab: WorkflowTab) => void;
}) {
  const completed = runs.filter((run) => run.status === "succeeded").length;
  return (
    <div className="workflow-section">
      <div className="workflow-metric-grid">
      <WorkflowMetric
        label="发布版本"
        value={detail.publishedVersion ? `V${detail.publishedVersion}` : "未发布"}
        note={detail.publishedVersion ? "当前正式运行版本" : "发布后方可启用"}
        />
        <WorkflowMetric
          label="流程节点"
          value={String(detail.definition.nodes.length)}
          note={`${detail.definition.edges.length} 条依赖连接`}
        />
        <WorkflowMetric
          label="累计运行"
          value={String(runs.length)}
          note={`${completed} 次成功完成`}
        />
        <WorkflowMetric
          label="成功率"
          value={`${detail.metrics.successRate}%`}
          note="最近 1000 次运行"
        />
      </div>
      <div className="workflow-overview-grid">
        <article className="workflow-panel">
          <header>
            <div>
              <h3>当前发布流程</h3>
              <p>正式任务严格使用该不可变快照。</p>
            </div>
            <button className="ghost-button" onClick={() => onTab("designer")}>
              查看流程
              <ChevronRight size={14} />
            </button>
          </header>
          <CompactFlow nodes={detail.definition.nodes} />
        </article>
        <aside className="workflow-panel workflow-summary-panel">
          <h3>配置摘要</h3>
          <dl>
            <div>
              <dt>业务场景</dt>
              <dd>{workflowSceneLabel(detail.scene)}</dd>
            </div>
            <div>
              <dt>草稿修订</dt>
              <dd>R{draft.revision}</dd>
            </div>
            <div>
              <dt>绑定 Skill</dt>
              <dd>
                {detail.definition.nodes.reduce(
                  (total, node) => total + (node.skillBindings?.length ?? 0),
                  0,
                )}{" "}
                项
              </dd>
            </div>
            <div>
              <dt>校验状态</dt>
              <dd className={detail.validation.valid ? "ok" : "bad"}>
                {detail.validation.valid ? "已通过" : "未通过"}
              </dd>
            </div>
          </dl>
          <button className="primary-button" onClick={() => onTab("versions")}>
            <Rocket size={15} />
            版本与发布
          </button>
        </aside>
      </div>
    </div>
  );
}
