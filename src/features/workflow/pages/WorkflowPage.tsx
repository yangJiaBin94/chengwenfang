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
import { formatWorkflowMetricDuration } from "../../../shared/format/duration";
import { formatWorkflowDateTime } from "../../../shared/format/time";
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
import { CUSTOM_AI_TYPE_KEY, isCustomAiNode } from "../api";
import {
  workflowSceneLabel,
  type CourseOption,
} from "../components/workflowUi";
import { WorkflowDetailPage } from "../components/WorkflowDetailPage";

const formatDate = formatWorkflowDateTime;
const formatDuration = formatWorkflowMetricDuration;

export function WorkflowPage({
  requestedId,
  courses,
  onOpenWorkflow,
  onOpenExecution,
  showNotice,
  confirmAction,
}: {
  requestedId: string | null;
  courses: CourseOption[];
  onOpenWorkflow: (id: string | null) => void;
  onOpenExecution: (jobId: string) => void;
  showNotice: (title: string, message: string) => void;
  confirmAction: (
    title: string,
    message: string,
    tone?: "default" | "danger",
  ) => Promise<boolean>;
}) {
  const [items, setItems] = useState<WorkflowSummary[]>([]);
  const [query, setQuery] = useState("");
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [creating, setCreating] = useState(false);
  const [createName, setCreateName] = useState("");
  const [createDescription, setCreateDescription] = useState("");

  async function refresh() {
    setLoading(true);
    setError("");
    try {
      const workflows = await listWorkflows();
      setItems(workflows);
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : "工作流加载失败");
    } finally {
      setLoading(false);
    }
  }

  // 进入工作流列表页时自动刷新一次（含从详情返回）
  useEffect(() => {
    if (requestedId) {
      return;
    }
    void refresh();
  }, [requestedId]);

  if (requestedId) {
    return (
      <WorkflowDetailPage
        workflowId={requestedId}
        courses={courses}
        onBack={() => onOpenWorkflow(null)}
        onOpenExecution={onOpenExecution}
        showNotice={showNotice}
        confirmAction={confirmAction}
      />
    );
  }

  const visible = items.filter((item) =>
    `${item.name} ${item.description} ${item.scene}`
      .toLowerCase()
      .includes(query.toLowerCase()),
  );
  return (
    <section className="workflow-page workflow-list-page">
      <header className="workflow-page-head">
        <div>
          <div className="context-line">课程体系教学模式 / 工作流</div>
          <span className="section-kicker">Workflow Center</span>
          <h2>工作流</h2>
          <p>集中查看内容生产流程、发布版本与运行质量。</p>
        </div>
        <div className="workflow-head-actions">
          <button className="ghost-button" onClick={() => void refresh()}>
            <RefreshCcw size={15} />
            刷新
          </button>
        </div>
      </header>
      <div className="workflow-toolbar">
        <label className="workflow-search">
          <Search size={17} />
          <input
            value={query}
            onChange={(event) => setQuery(event.target.value)}
            placeholder="搜索工作流名称或场景"
          />
        </label>
        <span>{visible.length} 个工作流</span>
      </div>
      {loading && (
        <div className="workflow-empty">
          <RefreshCcw className="spin" size={22} />
          正在读取工作流
        </div>
      )}
      {error && (
        <div className="workflow-empty error">
          <AlertTriangle size={22} />
          {error}
        </div>
      )}
      {!loading && !error && (
        <div className="workflow-table">
          <div className="workflow-table-head">
            <span>名称</span>
            <span>场景</span>
            <span>发布版本</span>
            <span>状态</span>
            <span>最后更新</span>
            <span>操作</span>
          </div>
          {visible.map((item) => (
            <div className="workflow-table-row" key={item.id}>
              <div>
                <strong>{item.name}</strong>
                <small>{item.description}</small>
              </div>
              <span>{workflowSceneLabel(item.scene)}</span>
              <span>V{item.publishedVersion ?? "-"}</span>
              <span>
                <i className={`workflow-state ${item.status}`}>
                  {item.status === "active" ? "运行中" : "已停用"}
                </i>
              </span>
              <span>{formatDate(item.updatedAt)}</span>
              <button
                className="table-link"
                onClick={() => onOpenWorkflow(item.id)}
              >
                查看
                <ChevronRight size={14} />
              </button>
            </div>
          ))}
        </div>
      )}
      {creating && (
        <div
          className="modal-backdrop"
          role="presentation"
          onMouseDown={() => setCreating(false)}
        >
          <section
            className="modal-card workflow-create-modal"
            role="dialog"
            aria-modal="true"
            aria-labelledby="workflow-create-title"
            onMouseDown={(event) => event.stopPropagation()}
          >
            <header>
              <div>
                <span className="section-kicker">受控工作流</span>
                <h3 id="workflow-create-title">新增工作流</h3>
                <p>新工作流默认停用，发布版本后才能用于正式任务。</p>
              </div>
              <button
                className="icon-button"
                aria-label="关闭"
                onClick={() => setCreating(false)}
              >
                ×
              </button>
            </header>
            <label>
              名称
              <input
                autoFocus
                value={createName}
                onChange={(event) => setCreateName(event.target.value)}
                placeholder="例如：课程内容生成实验流程"
              />
            </label>
            <label>
              功能说明
              <textarea
                value={createDescription}
                onChange={(event) => setCreateDescription(event.target.value)}
                placeholder="简要说明适用范围和目标"
              />
            </label>
            <footer>
              <button
                className="ghost-button"
                onClick={() => setCreating(false)}
              >
                取消
              </button>
              <button
                className="primary-button"
                disabled={!createName.trim()}
                onClick={async () => {
                  try {
                    const item = await createWorkflow(
                      createName.trim(),
                      createDescription.trim(),
                    );
                    setCreating(false);
                    setCreateName("");
                    setCreateDescription("");
                    await refresh();
                    onOpenWorkflow(item.id);
                  } catch (reason) {
                    showNotice(
                      "创建失败",
                      reason instanceof Error
                        ? reason.message
                        : "工作流创建失败",
                    );
                  }
                }}
              >
                <Plus size={15} />
                创建工作流
              </button>
            </footer>
          </section>
        </div>
      )}
    </section>
  );
}


