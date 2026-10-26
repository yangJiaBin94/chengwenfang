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

import { tabs, runLabels, workflowSceneLabel, type WorkflowTab, type CourseOption, nextVersion } from "./workflowUi";
import { WorkflowOverview } from "./WorkflowOverview";
import { WorkflowDesigner } from "./WorkflowDesigner";
import { WorkflowVersions } from "./WorkflowVersions";
import { WorkflowRuns } from "./WorkflowRuns";
import { WorkflowAnalytics } from "./WorkflowAnalytics";

export function WorkflowDetailPage({
  workflowId,
  courses,
  onBack,
  onOpenExecution,
  showNotice,
  confirmAction,
}: {
  workflowId: string;
  courses: CourseOption[];
  onBack: () => void;
  onOpenExecution: (jobId: string) => void;
  showNotice: (title: string, message: string) => void;
  confirmAction: (
    title: string,
    message: string,
    tone?: "default" | "danger",
  ) => Promise<boolean>;
}) {
  const [tab, setTab] = useState<WorkflowTab>("overview");
  const [detail, setDetail] = useState<WorkflowDetail | null>(null);
  const [draft, setDraft] = useState<WorkflowDraft | null>(null);
  const [versions, setVersions] = useState<WorkflowVersion[]>([]);
  const [runs, setRuns] = useState<WorkflowRun[]>([]);
  const [nodeMetrics, setNodeMetrics] = useState<WorkflowNodeMetric[]>([]);
  const [versionMetrics, setVersionMetrics] = useState<WorkflowVersionMetric[]>(
    [],
  );
  const [alerts, setAlerts] = useState<WorkflowAlert[]>([]);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [dirty, setDirty] = useState(false);

  async function loadAll() {
    setBusy(true);
    setError("");
    try {
      const [
        nextDetail,
        nextDraft,
        nextVersions,
        nextRuns,
        nextNodeMetrics,
        nextVersionMetrics,
        nextAlerts,
      ] = await Promise.all([
        loadWorkflow(workflowId),
        loadWorkflowDraft(workflowId),
        listWorkflowVersions(workflowId),
        listWorkflowRuns(workflowId),
        loadWorkflowNodeMetrics(workflowId),
        loadWorkflowVersionMetrics(workflowId),
        listWorkflowAlerts(workflowId),
      ]);
      setDetail(nextDetail);
      setDraft(nextDraft);
      setVersions(nextVersions);
      setDirty(false);
      setRuns(nextRuns);
      setNodeMetrics(nextNodeMetrics);
      setVersionMetrics(nextVersionMetrics);
      setAlerts(nextAlerts);
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : "工作流详情加载失败");
    } finally {
      setBusy(false);
    }
  }
  useEffect(() => {
    void loadAll();
  }, [workflowId]);
  useEffect(() => {
    const guard = (event: BeforeUnloadEvent) => {
      if (dirty) event.preventDefault();
    };
    window.addEventListener("beforeunload", guard);
    return () => window.removeEventListener("beforeunload", guard);
  }, [dirty]);

  if (!detail || !draft)
    return (
      <section className="workflow-page">
        <button className="back-button" onClick={onBack}>
          <ArrowLeft size={15} />
          返回工作流
        </button>
        <div className={`workflow-empty ${error ? "error" : ""}`}>
          {error ? (
            <AlertTriangle size={22} />
          ) : (
            <RefreshCcw className="spin" size={22} />
          )}
          {error || "正在加载工作流详情"}
        </div>
      </section>
    );

  return (
    <section className="workflow-page">
      <header className="workflow-detail-head">
        <button
          className="back-button"
          onClick={async () => {
            if (
              dirty &&
              !(await confirmAction(
                "离开未保存草稿",
                "当前流程修改尚未保存，离开后将丢失。",
                "danger",
              ))
            )
              return;
            onBack();
          }}
        >
          <ArrowLeft size={15} />
          返回工作流
        </button>
        <div className="workflow-title-line">
          <span className="workflow-title-icon">
            <Workflow size={21} />
          </span>
          <div>
            <span className="section-kicker">{workflowSceneLabel(detail.scene)}</span>
            <h2>{detail.name}</h2>
            <p>{detail.description}</p>
          </div>
        </div>
        <div className="workflow-head-status">
          <span className={`workflow-state ${detail.status}`}>
            {detail.status === "active"
              ? "运行中"
              : detail.status === "archived"
                ? "已归档"
                : "已停用"}
          </span>
          <b>V{detail.publishedVersion ?? "-"}</b>
          <button
            className="ghost-button"
            disabled={detail.status === "archived" || (!detail.publishedVersionId && detail.status !== "active")}
            title={!detail.publishedVersionId ? "请先发布一个版本" : undefined}
            onClick={async () => {
              const next = detail.status === "active" ? "inactive" : "active";
              if (
                !(await confirmAction(
                  next === "active" ? "启用工作流" : "停用工作流",
                  `确认${next === "active" ? "启用" : "停用"}“${detail.name}”？`,
                ))
              )
                return;
              await updateWorkflow(workflowId, { status: next });
              await loadAll();
            }}
          >
            <Power size={14} />
            {detail.status === "active" ? "停用" : "启用"}
          </button>
        </div>
      </header>
      <nav className="workflow-tabs" aria-label="工作流功能">
        {tabs.map((item) => {
          const Icon = item.icon;
          return (
            <button
              className={tab === item.key ? "active" : ""}
              key={item.key}
              onClick={() => setTab(item.key)}
            >
              <Icon size={16} />
              {item.label}
            </button>
          );
        })}
      </nav>
      {error && (
        <div className="workflow-banner error">
          <AlertTriangle size={17} />
          {error}
        </div>
      )}
      {tab === "overview" && (
        <WorkflowOverview
          detail={detail}
          draft={draft}
          runs={runs}
          onTab={setTab}
        />
      )}
      {tab === "designer" && (
        <WorkflowDesigner
          draft={draft}
          busy={busy}
          onDraft={(next) => {
            setDraft(next);
            setDirty(true);
          }}
          onSave={async () => {
            setBusy(true);
            try {
              const saved = await saveWorkflowDraft(workflowId, draft);
              setDraft(saved);
              setDirty(false);
              showNotice("草稿已保存", `当前草稿修订号为 ${saved.revision}`);
            } catch (reason) {
              showNotice(
                "保存失败",
                reason instanceof Error ? reason.message : "草稿保存失败",
              );
            } finally {
              setBusy(false);
            }
          }}
          onValidate={async () => {
            const report = await validateWorkflow(workflowId, draft.definition);
            setDraft({ ...draft, validation: report });
          }}
          onReset={async () => {
            if (!(await confirmAction("重置草稿", "确认丢弃全部未发布修改并恢复当前发布版本？", "danger"))) return;
            const reset = await resetWorkflowDraft(workflowId, draft);
            setDraft(reset); setDirty(false);
            showNotice("草稿已重置", `当前草稿修订号为 ${reset.revision}`);
          }}
        />
      )}
      {tab === "versions" && (
        <WorkflowVersions
          versions={versions}
          draft={draft}
          currentId={detail.publishedVersionId}
          onPublish={async (version, notes) => {
            if (
              !(await confirmAction(
                "发布工作流",
                `确认将当前草稿发布为 V${version}？发布后只影响新的生成任务。`,
              ))
            )
              return;
            setBusy(true);
            try {
              await publishWorkflow(workflowId, version, notes);
              await loadAll();
              showNotice("发布成功", `工作流 V${version} 已生效`);
            } catch (reason) {
              showNotice(
                "发布失败",
                reason instanceof Error ? reason.message : "发布失败",
              );
            } finally {
              setBusy(false);
            }
          }}
          onRestore={async (version) => {
            if (
              !(await confirmAction(
                "恢复为草稿",
                `将 V${version.version} 的配置复制为新草稿？当前草稿会被替换。`,
                "danger",
              ))
            )
              return;
            const restored = await restoreWorkflowVersion(
              workflowId,
              version.id,
            );
            setDraft(restored);
            setTab("designer");
          }}
        />
      )}
      {tab === "runs" && (
        <WorkflowRuns runs={runs} onOpenExecution={onOpenExecution} />
      )}
      {tab === "analytics" && (
        <WorkflowAnalytics
          metrics={detail.metrics}
          runs={runs}
          nodeMetrics={nodeMetrics}
          versionMetrics={versionMetrics}
          alerts={alerts}
          onOpenExecution={onOpenExecution}
          onResolveAlert={async (alert) => {
            await resolveWorkflowAlert(workflowId, alert.id);
            await loadAll();
          }}
        />
      )}
    </section>
  );
}
