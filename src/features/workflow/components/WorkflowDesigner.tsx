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

import { ValidationBanner } from "./workflowUi";

export function WorkflowDesigner({
  draft,
  busy,
  onDraft,
  onSave,
  onValidate,
  onReset,
}: {
  draft: WorkflowDraft;
  busy: boolean;
  onDraft: (draft: WorkflowDraft) => void;
  onSave: () => Promise<void>;
  onValidate: () => Promise<void>;
  onReset: () => Promise<void>;
}) {
  const [selectedKey, setSelectedKey] = useState(
    draft.definition.nodes[0]?.key ?? "",
  );
  const [nodeTypes, setNodeTypes] = useState<WorkflowNodeType[]>([]);
  const [past, setPast] = useState<WorkflowDraft["definition"][]>([]);
  const [future, setFuture] = useState<WorkflowDraft["definition"][]>([]);
  const [skillCatalog, setSkillCatalog] = useState<Skill[]>([]);
  const [skillPickerOpen, setSkillPickerOpen] = useState(false);
  const [skillQuery, setSkillQuery] = useState("");
  const [pickedSkillIds, setPickedSkillIds] = useState<string[]>([]);
  const [skillCatalogError, setSkillCatalogError] = useState("");
  useEffect(() => {
    void listWorkflowNodeTypes().then(setNodeTypes);
    // 预加载 Skill 目录，便于节点绑定列表按 id 显示真实名称
    void listSkills().then(setSkillCatalog).catch(() => undefined);
  }, []);
  const selected = draft.definition.nodes.find(
    (node) => node.key === selectedKey,
  );
  const selectedIsCustom = selected ? isCustomAiNode(selected) : false;
  const selectedType = nodeTypes.find((item) => item.key === selected?.typeKey);
  const systemSkillIds = useMemo(
    () => new Set(skillCatalog.filter((skill) => skill.scope === "system").map((skill) => skill.id)),
    [skillCatalog],
  );
  const commit = (definition: WorkflowDraft["definition"]) => {
    setPast((items) => [...items.slice(-29), draft.definition]);
    setFuture([]);
    onDraft({ ...draft, definition });
  };
  const undo = () => {
    const previous = past.at(-1);
    if (!previous) return;
    setPast((items) => items.slice(0, -1));
    setFuture((items) => [draft.definition, ...items]);
    onDraft({ ...draft, definition: previous });
  };
  const redo = () => {
    const next = future[0];
    if (!next) return;
    setFuture((items) => items.slice(1));
    setPast((items) => [...items, draft.definition]);
    onDraft({ ...draft, definition: next });
  };
  const updateNode = (next: WorkflowDefinitionNode) => {
    const edges = draft.definition.edges.map((edge) => {
      if (next.typeKey === "controlled_condition" && edge.source === next.key) return { ...edge, condition: { field: String(next.config.conditionField ?? "reviewPassed"), operator: String(next.config.conditionOperator ?? "eq"), value: next.config.conditionValue ?? true } };
      return edge.loop && edge.source === next.key
        ? {
            ...edge,
            maxIterations: Number(
              next.config.maxIterations ?? edge.maxIterations ?? 2,
            ),
          }
        : edge;
    });
    commit({
      ...draft.definition,
      nodes: draft.definition.nodes.map((node) =>
        node.key === next.key ? next : node,
      ),
      edges,
    });
  };
  /** 按线性顺序重建 sequenceNo、position 与主链 edges。 */
  const normalizeLinearGraph = (nodes: WorkflowDefinitionNode[]) => {
    const normalized = nodes.map((node, index) => ({
      ...node,
      sequenceNo: index + 1,
      position: { x: 120, y: (index + 1) * 140 },
    }));
    const edges: WorkflowDefinitionEdge[] = normalized.slice(0, -1).map((node, index) => ({
      id: `edge-${index + 1}`,
      source: node.key,
      sourcePort: "output",
      target: normalized[index + 1].key,
      targetPort: "input",
    }));
    return { nodes: normalized, edges };
  };
  const move = (key: string, offset: number) => {
    const source = draft.definition.nodes.find((node) => node.key === key);
    if (!source || !isCustomAiNode(source)) return;
    const nodes = [...draft.definition.nodes];
    const index = nodes.findIndex((node) => node.key === key);
    const target = index + offset;
    if (index < 0 || target < 0 || target >= nodes.length) return;
    // 自定义节点不得越过相邻原生节点互换以外的约束：仅与相邻位交换
    [nodes[index], nodes[target]] = [nodes[target], nodes[index]];
    const next = normalizeLinearGraph(nodes);
    commit({ ...draft.definition, nodes: next.nodes, edges: next.edges });
  };
  const insertCustomNode = (anchorKey: string, where: "before" | "after") => {
    const nodes = [...draft.definition.nodes];
    const anchorIndex = nodes.findIndex((node) => node.key === anchorKey);
    if (anchorIndex < 0) return;
    const key = `${CUSTOM_AI_TYPE_KEY}-${Date.now()}`;
    const node: WorkflowDefinitionNode = {
      key,
      typeKey: CUSTOM_AI_TYPE_KEY,
      name: "自定义节点",
      description: "",
      stage: "自定义",
      sequenceNo: 0,
      enabled: true,
      config: {
        timeoutSeconds: 120,
        maxAttempts: 1,
        failurePolicy: "stop",
        prompt: "",
      },
      position: { x: 120, y: 140 },
      skillBindings: [],
    };
    const insertAt = where === "before" ? anchorIndex : anchorIndex + 1;
    nodes.splice(insertAt, 0, node);
    const next = normalizeLinearGraph(nodes);
    commit({ ...draft.definition, nodes: next.nodes, edges: next.edges });
    setSelectedKey(key);
  };
  const removeSelected = () => {
    if (!selected || !isCustomAiNode(selected)) return;
    const nodes = draft.definition.nodes.filter((node) => node.key !== selected.key);
    const next = normalizeLinearGraph(nodes);
    commit({ ...draft.definition, nodes: next.nodes, edges: next.edges });
    setSelectedKey(next.nodes[0]?.key ?? "");
  };
  const changeSkill = (skillId: string, action: "toggle" | "up" | "down" | "remove") => {
    if (!selected) return;
    const skills = [...(selected.skillBindings ?? [])];
    const index = skills.findIndex((skill) => skill.skillId === skillId);
    if (index < 0 || systemSkillIds.has(skillId) || skills[index].priority <= 0) return;
    if (action === "remove") {
      updateNode({ ...selected, skillBindings: skills.filter((skill) => skill.skillId !== skillId) });
      return;
    }
    if (action === "toggle") skills[index] = { ...skills[index], enabled: !skills[index].enabled };
    const target = action === "up" ? index - 1 : action === "down" ? index + 1 : index;
    if (target !== index && target >= 0 && target < skills.length && skills[target].priority > 0) [skills[index], skills[target]] = [skills[target], skills[index]];
    const normalized = skills.map((skill, order) => skill.priority <= 0 ? skill : { ...skill, priority: (order + 1) * 10 });
    updateNode({ ...selected, skillBindings: normalized });
  };
  const openSkillPicker = async () => {
    setSkillCatalogError("");
    setSkillQuery("");
    setPickedSkillIds([]);
    setSkillPickerOpen(true);
    try {
      setSkillCatalog(await listSkills());
    } catch (reason) {
      setSkillCatalogError(reason instanceof Error ? reason.message : "读取 Skill 失败");
    }
  };
  const addPickedSkills = () => {
    if (!selected || !pickedSkillIds.length) return;
    const current = selected.skillBindings ?? [];
    const existing = new Set(current.map((binding) => binding.skillId));
    const sceneCount = current.filter((binding) => !systemSkillIds.has(binding.skillId) && binding.priority > 0).length;
    const additions = skillCatalog
      .filter((skill) => pickedSkillIds.includes(skill.id) && !existing.has(skill.id))
      .map((skill, index) => ({ skillId: skill.id, name: skill.name, priority: (sceneCount + index + 1) * 10, enabled: true }));
    updateNode({ ...selected, skillBindings: [...current, ...additions] });
    setSkillPickerOpen(false);
  };
  const availableSkills = skillCatalog.filter(
    (skill) => skill.scope === "scene" && skill.scene === draft.definition.scene &&
      `${skill.name} ${skill.description}`.toLowerCase().includes(skillQuery.trim().toLowerCase()),
  );
  return (
    <div className="workflow-section">
      <div className="workflow-actionbar">
        <div>
          <strong>草稿 R{draft.revision}</strong>
          <span>修改只进入草稿，发布后才影响正式任务。</span>
        </div>
        <span className="workflow-history-actions">
          <button className="icon-button" title="撤销" aria-label="撤销" disabled={!past.length} onClick={undo}><Undo2 size={16} /></button>
          <button className="icon-button" title="重做" aria-label="重做" disabled={!future.length} onClick={redo}><Redo2 size={16} /></button>
        </span>
        <button className="ghost-button" onClick={() => void onValidate()}>
          <ShieldCheck size={15} />
          校验
        </button>
        <button className="ghost-button" disabled={busy} onClick={() => void onReset()}><RefreshCcw size={15} />重置</button>
        <button
          className="primary-button"
          disabled={busy}
          onClick={() => void onSave()}
        >
          <Save size={15} />
          保存草稿
        </button>
      </div>
      {draft.validation && <ValidationBanner report={draft.validation} />}
      <div className="workflow-designer">
        <div className="workflow-canvas" aria-label="工作流节点画布">
          <div className="workflow-canvas-tools">
            <span>
              <GitBranch size={15} />
              {draft.definition.nodes.length} 个节点 ·{" "}
              {draft.definition.edges.length} 条连接
            </span>
            <small>选中节点后可在其上方或下方插入自定义节点</small>
          </div>
          <div className="workflow-canvas-flow">
            {draft.definition.nodes.map((node, index) => {
              const outgoing = draft.definition.edges.filter(
                (edge) => edge.source === node.key,
              );
              const custom = isCustomAiNode(node);
              const selectedHere = selectedKey === node.key;
              return (
                <div className="workflow-canvas-step" key={node.key}>
                  {selectedHere && (
                    <div className="workflow-insert-actions">
                      <button type="button" onClick={() => insertCustomNode(node.key, "before")}>
                        上方插入
                      </button>
                      <button type="button" onClick={() => insertCustomNode(node.key, "after")}>
                        下方插入
                      </button>
                    </div>
                  )}
                  <button
                    className={`${selectedHere ? "selected" : ""} ${node.enabled ? "" : "disabled"} ${custom ? "custom-ai" : "native"}`}
                    onClick={() => setSelectedKey(node.key)}
                  >
                    <i>{index + 1}</i>
                    <span>
                      <b>{node.name}</b>
                      <small>
                        {custom ? "自定义节点" : node.stage} ·{" "}
                        {node.skillBindings?.length ?? 0} 个 Skill
                      </small>
                    </span>
                    <em>{custom ? "自定义" : "原生"}</em>
                  </button>
                  {outgoing.map((edge) => (
                    <span
                      className={`workflow-edge ${edge.loop ? "loop" : ""}`}
                      key={edge.id}
                    >
                      <i />
                      {edge.condition
                        ? `${edge.condition.field} ${edge.condition.operator} ${String(edge.condition.value)}`
                        : edge.loop
                          ? `返工上限 ${edge.maxIterations ?? 1} 次`
                          : "数据流"}
                    </span>
                  ))}
                </div>
              );
            })}
          </div>
        </div>
        <aside className="workflow-node-config">
          {selected ? (
            <>
              <header>
                <Settings2 size={17} />
                <div>
                  <h3>{selected.name}</h3>
                  <small>{selectedIsCustom ? "自定义 AI 节点" : selected.typeKey}</small>
                </div>
              </header>
              {selectedType && !selectedIsCustom && (
                <dl className="workflow-executor-meta">
                  <div>
                    <dt>执行器版本</dt>
                    <dd>V{selectedType.executorVersion}</dd>
                  </div>
                  <div>
                    <dt>模型类型</dt>
                    <dd>
                      {selectedType.modelType === "text"
                        ? "文本 AI"
                        : selectedType.modelType === "image"
                          ? "图片 AI"
                          : "确定性代码"}
                    </dd>
                  </div>
                  <div>
                    <dt>配置等级</dt>
                    <dd>{selectedType.configurability} 级</dd>
                  </div>
                  <div>
                    <dt>外部副作用</dt>
                    <dd>{selectedType.sideEffects ? "有副作用" : "无"}</dd>
                  </div>
                </dl>
              )}
              <label>
                节点名称
                <input
                  value={selected.name}
                  onChange={(event) =>
                    updateNode({ ...selected, name: event.target.value })
                  }
                />
              </label>
              {selectedIsCustom ? (
                <label>
                  节点提示词
                  <textarea
                    value={String(selected.config.prompt ?? "")}
                    onChange={(event) =>
                      updateNode({
                        ...selected,
                        config: {
                          ...selected.config,
                          prompt: event.target.value,
                        },
                      })
                    }
                    rows={8}
                    placeholder="描述本节点要对文章流程做的事……"
                  />
                  <small className="workflow-custom-boundary-hint">
                    仅允许：读取已配置 Skill、骨架、简报、资料库、当前原稿/公众号（若已生成）；修改原稿或公众号（若当前阶段已生成）。超出范围或不支持的动作将导致本节点执行失败。
                  </small>
                </label>
              ) : (
                <label>
                  功能说明
                  <textarea
                    value={selected.description}
                    onChange={(event) =>
                      updateNode({ ...selected, description: event.target.value })
                    }
                  />
                </label>
              )}
              <div className="workflow-field-row">
                <label>
                  超时（秒）
                  <input
                    type="number"
                    min={10}
                    max={900}
                    value={selected.config.timeoutSeconds}
                    onChange={(event) =>
                      updateNode({
                        ...selected,
                        config: {
                          ...selected.config,
                          timeoutSeconds: Number(event.target.value),
                        },
                      })
                    }
                  />
                </label>
                <label>
                  最大尝试
                  <input
                    type="number"
                    min={1}
                    max={5}
                    value={selected.config.maxAttempts}
                    onChange={(event) =>
                      updateNode({
                        ...selected,
                        config: {
                          ...selected.config,
                          maxAttempts: Number(event.target.value),
                        },
                      })
                    }
                  />
                </label>
              </div>
              <label>
                失败策略
                <select
                  value={selected.config.failurePolicy}
                  onChange={(event) =>
                    updateNode({
                      ...selected,
                      config: {
                        ...selected.config,
                        failurePolicy: event.target
                          .value as WorkflowDefinitionNode["config"]["failurePolicy"],
                      },
                    })
                  }
                >
                  <option value="stop">失败后停止</option>
                  <option value="retry_then_stop">重试后停止</option>
                  <option value="continue_with_warning">警告并继续</option>
                </select>
              </label>
              {selected.typeKey === "controlled_condition" && (
                <div className="workflow-condition-box">
                  <b>声明式判断</b>
                  <label>
                    判断字段
                    <select
                      value={String(
                        selected.config.conditionField ?? "reviewPassed",
                      )}
                      onChange={(event) =>
                        updateNode({
                          ...selected,
                          config: {
                            ...selected.config,
                            conditionField: event.target.value,
                          },
                        })
                      }
                    >
                      <option value="reviewPassed">审校是否通过</option>
                      <option value="status">节点状态</option>
                      <option value="attemptNo">尝试次数</option>
                    </select>
                  </label>
                  <div className="workflow-field-row"><label>比较方式<select value={String(selected.config.conditionOperator ?? "eq")} onChange={(event) => updateNode({ ...selected, config: { ...selected.config, conditionOperator: event.target.value } })}><option value="eq">等于</option><option value="ne">不等于</option><option value="lt">小于</option><option value="gte">大于等于</option></select></label><label>目标值<select value={String(selected.config.conditionValue ?? true)} onChange={(event) => updateNode({ ...selected, config: { ...selected.config, conditionValue: event.target.value === "true" } })}><option value="true">是</option><option value="false">否</option></select></label></div>
                  <small>仅允许白名单字段比较，不支持脚本表达式。</small>
                </div>
              )}
              {selected.typeKey === "bounded_loop" && (
                <div className="workflow-condition-box">
                  <b>返工上限</b>
                  <label>
                    最大迭代次数
                    <input
                      type="number"
                      min={1}
                      max={5}
                      value={Number(selected.config.maxIterations ?? 2)}
                      onChange={(event) =>
                        updateNode({
                          ...selected,
                          config: {
                            ...selected.config,
                            maxIterations: Number(event.target.value),
                          },
                        })
                      }
                    />
                  </label>
                  <small>达到上限后流程明确失败，避免无限调用模型。</small>
                </div>
              )}
              <div className="workflow-skill-box">
                <header>
                  <span><b>节点 Skill</b><small>保存到草稿，发布版本后生效</small></span>
                  <button type="button" onClick={() => void openSkillPicker()}><Plus size={13} />添加 Skill</button>
                </header>
                {selected.skillBindings?.length ? (
                  selected.skillBindings.map((skill) => {
                    const systemSkill = systemSkillIds.has(skill.skillId) || skill.priority <= 0;
                    const catalogName = skillCatalog.find((item) => item.id === skill.skillId)?.name;
                    const displayName = skill.name || catalogName || `未命名 Skill（${skill.skillId.slice(0, 8)}）`;
                    return <div className={skill.enabled ? "" : "disabled"} key={skill.skillId}>
                      <span><strong>{displayName}</strong><small>{systemSkill ? "系统级 · 只读" : `执行顺序 ${skill.priority}`}</small></span>
                      {!systemSkill && <span className="workflow-skill-actions"><button title="上移" onClick={() => changeSkill(skill.skillId, "up")}>↑</button><button title="下移" onClick={() => changeSkill(skill.skillId, "down")}>↓</button><button onClick={() => changeSkill(skill.skillId, "toggle")}>{skill.enabled ? "停用" : "启用"}</button><button className="danger" title="移除" onClick={() => changeSkill(skill.skillId, "remove")}><Trash2 size={12} /></button></span>}
                    </div>;
                  })
                ) : (
                  <small>该节点没有绑定场景 Skill</small>
                )}
              </div>
              {selectedIsCustom && (
                <>
                  <div className="workflow-order-actions">
                    <button type="button" onClick={() => move(selected.key, -1)}>上移</button>
                    <button type="button" onClick={() => move(selected.key, 1)}>下移</button>
                  </div>
                  <button
                    className="workflow-remove-node"
                    type="button"
                    onClick={removeSelected}
                  >
                    删除自定义节点
                  </button>
                </>
              )}
            </>
          ) : (
            <div className="workflow-empty">请选择节点</div>
          )}
        </aside>
      </div>
      {skillPickerOpen && (
        <div className="modal-backdrop" onClick={() => setSkillPickerOpen(false)}>
          <section className="workflow-skill-picker" role="dialog" aria-modal="true" aria-labelledby="workflow-skill-picker-title" onClick={(event) => event.stopPropagation()}>
            <header><div><small>工作流节点配置</small><h3 id="workflow-skill-picker-title">为“{selected?.name}”添加 Skill</h3></div><button className="icon-button" aria-label="关闭" onClick={() => setSkillPickerOpen(false)}><X size={17} /></button></header>
            <div className="workflow-skill-search"><Search size={16} /><input autoFocus value={skillQuery} onChange={(event) => setSkillQuery(event.target.value)} placeholder="搜索名称或功能简介" /></div>
            <p>仅显示当前工作流场景的 Skill。全局关闭的 Skill 可以绑定，但执行时不会注入。</p>
            <div className="workflow-skill-options">
              {skillCatalogError && <span className="workflow-picker-error">{skillCatalogError}</span>}
              {!skillCatalogError && availableSkills.map((skill) => {
                const bound = selected?.skillBindings?.some((binding) => binding.skillId === skill.id) ?? false;
                const picked = pickedSkillIds.includes(skill.id);
                return <button type="button" className={picked ? "selected" : ""} disabled={bound} aria-pressed={picked} key={skill.id} onClick={() => setPickedSkillIds((ids) => ids.includes(skill.id) ? ids.filter((id) => id !== skill.id) : [...ids, skill.id])}><span><b>{skill.name}</b><small>{skill.description || "暂无功能简介"}</small></span><em>{bound ? "已绑定" : skill.enabled ? picked ? "已选择" : "可添加" : "全局关闭"}</em></button>;
              })}
              {!skillCatalogError && !availableSkills.length && <span className="workflow-picker-empty">没有符合条件的 Skill</span>}
            </div>
            <footer><span>已选择 {pickedSkillIds.length} 项</span><div><button className="ghost-button" onClick={() => setSkillPickerOpen(false)}>取消</button><button className="primary-button" disabled={!pickedSkillIds.length} onClick={addPickedSkills}>确认添加</button></div></footer>
          </section>
        </div>
      )}
    </div>
  );
}
