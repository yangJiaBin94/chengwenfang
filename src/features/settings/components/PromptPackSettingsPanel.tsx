import { useEffect, useRef, useState } from "react";
import {
  Check,
  ChevronDown,
  ChevronLeft,
  ChevronRight,
  Download,
  Eye,
  FileText,
  Image,
  Search,
  Upload,
  X,
} from "lucide-react";
import { testAiConnection } from "../api";
import {
  loadWechatMpSettings,
  saveWechatMpSettings,
  testWechatMpConnection,
} from "../api";
import type { WechatMpSettings } from "../api";
import {
  listLayoutThemes,
  cloneLayoutTheme,
  createLayoutTheme,
  setLayoutThemeStatus,
  updateLayoutTheme,
  deleteLayoutTheme,
} from "../api";
import type { LayoutThemeData } from "../api";
import {
  imageProtocolOptions,
  textProtocolOptions,
  visionProtocolOptions,
} from "../model";
import type { AiProviderConfig, AiSettings, AiSettingsKind } from "../model";
import {
  createPromptPack,
  deletePromptPack,
  listPromptPacks,
  pagePromptPacks,
  updatePromptPack,
  updatePromptPack as savePromptPackApi,
} from "../api";
import type { PromptPack } from "../api";
import {
  LIST_PAGE_SIZE_OPTIONS,
  listPageNumbers,
  type ListPageSize,
} from "../../../shared/api/pagination";
import { fetchBuiltinReady } from "../api";
import type { BuiltinReadyView } from "../api";
import { text } from "../../hotnews/components/hotNewsExecutionHelpers";

export type PromptPackApplication = PromptPack["applications"][number];

export function groupPromptPackApplications(applications: PromptPackApplication[]) {
  const groups = new Map<
    string,
    {
      workflowId: string;
      workflowName: string;
      modeName: string;
      nodes: Array<{ nodeKey: string; nodeName: string }>;
    }
  >();
  for (const application of applications) {
    const existing = groups.get(application.workflowId);
    if (existing) {
      existing.nodes.push({
        nodeKey: application.nodeKey,
        nodeName: application.nodeName,
      });
      continue;
    }
    groups.set(application.workflowId, {
      workflowId: application.workflowId,
      workflowName: application.workflowName,
      modeName: application.modeName,
      nodes: [
        { nodeKey: application.nodeKey, nodeName: application.nodeName },
      ],
    });
  }
  return [...groups.values()];
}

export function PromptPackApplicationSummary({
  applications,
  expanded,
  onToggle,
}: {
  applications: PromptPackApplication[];
  expanded: boolean;
  onToggle: () => void;
}) {
  if (!applications.length) {
    return (
      <span className="prompt-pack-application-summary">
        <small>未应用</small>
      </span>
    );
  }
  const groups = groupPromptPackApplications(applications);
  return (
    <span className="prompt-pack-application-summary">
      <button
        type="button"
        className="prompt-pack-application-toggle"
        aria-expanded={expanded}
        onClick={onToggle}
      >
        {expanded ? <ChevronDown size={14} /> : <ChevronRight size={14} />}
        <span>
          已用于 {groups.length} 个工作流 · 共 {applications.length} 个节点
        </span>
      </button>
      {expanded ? (
        <div className="prompt-pack-application-groups">
          {groups.map((group) => (
            <div className="prompt-pack-application-group" key={group.workflowId}>
              <strong title={`${group.modeName} / ${group.workflowName}`}>
                {group.workflowName}
              </strong>
              <div className="prompt-pack-application-nodes">
                {group.nodes.map((node) => (
                  <em key={node.nodeKey}>{node.nodeName}</em>
                ))}
              </div>
            </div>
          ))}
        </div>
      ) : null}
    </span>
  );
}

export function PromptPackSettingsPanel() {
  const [promptPacks, setPromptPacks] = useState<PromptPack[]>([]);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(true);
  const [query, setQuery] = useState("");
  const [debouncedQuery, setDebouncedQuery] = useState("");
  const [listPage, setListPage] = useState(1);
  const [pageSize, setPageSize] = useState<ListPageSize>(10);
  const [pageSizeOpen, setPageSizeOpen] = useState(false);
  const pageSizeRef = useRef<HTMLDivElement | null>(null);
  const [open, setOpen] = useState(false);
  const [editingPromptPack, setEditingPromptPack] = useState<PromptPack | null>(null);
  const [promptPackName, setPromptPackName] = useState("");
  const [promptPackInstructions, setPromptPackInstructions] = useState("");
  const [promptPackDescription, setPromptPackDescription] = useState("");
  const [formError, setFormError] = useState("");
  const [expandedApplicationIds, setExpandedApplicationIds] = useState(
    () => new Set<string>(),
  );

  // 点击外部关闭每页条数菜单
  useEffect(() => {
    if (!pageSizeOpen) return;
    function onPointerDown(event: MouseEvent) {
      if (!pageSizeRef.current?.contains(event.target as Node)) {
        setPageSizeOpen(false);
      }
    }
    function onKeyDown(event: KeyboardEvent) {
      if (event.key === "Escape") setPageSizeOpen(false);
    }
    document.addEventListener("mousedown", onPointerDown);
    document.addEventListener("keydown", onKeyDown);
    return () => {
      document.removeEventListener("mousedown", onPointerDown);
      document.removeEventListener("keydown", onKeyDown);
    };
  }, [pageSizeOpen]);

  // 搜索输入防抖后再打后端，并回到第一页
  useEffect(() => {
    const timer = window.setTimeout(() => {
      setDebouncedQuery(query.trim());
      setListPage(1);
    }, 300);
    return () => window.clearTimeout(timer);
  }, [query]);

  // 后端分页加载当前页
  useEffect(() => {
    let cancelled = false;
    (async () => {
      setLoading(true);
      try {
        const result = await pagePromptPacks({
          page: listPage,
          pageSize,
          keyword: debouncedQuery,
        });
        if (cancelled) return;
        setPromptPacks(result.items);
        setTotal(result.total);
        if (result.page !== listPage) {
          setListPage(result.page);
        }
      } catch {
        if (!cancelled) {
          setPromptPacks([]);
          setTotal(0);
        }
      } finally {
        if (!cancelled) setLoading(false);
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [listPage, pageSize, debouncedQuery]);

  const listPageCount = Math.max(1, Math.ceil(total / pageSize));

  async function reloadCurrentPage() {
    setLoading(true);
    try {
      const result = await pagePromptPacks({
        page: listPage,
        pageSize,
        keyword: debouncedQuery,
      });
      setPromptPacks(result.items);
      setTotal(result.total);
      if (result.page !== listPage) {
        setListPage(result.page);
      }
    } catch {
      setPromptPacks([]);
      setTotal(0);
    } finally {
      setLoading(false);
    }
  }

  function openCreatePromptPack() {
    setEditingPromptPack(null);
    setPromptPackName("");
    setPromptPackInstructions("");
    setPromptPackDescription("");
    setFormError("");
    setOpen(true);
  }
  function openEditPromptPack(promptPack : PromptPack) {
    setEditingPromptPack(promptPack);
    setPromptPackName(promptPack.name);
    setPromptPackInstructions(promptPack.systemInstructions);
    setPromptPackDescription(promptPack.description);
    setFormError("");
    setOpen(true);
  }
  async function saveForm() {
    if (!promptPackName.trim() || !promptPackInstructions.trim()) {
      setFormError("请填写名称和内容");
      return;
    }
    setFormError("");
    if (editingPromptPack) {
      await savePromptPackApi({
        ...editingPromptPack,
        name: promptPackName.trim(),
        description: promptPackDescription.trim(),
        systemInstructions: promptPackInstructions.trim(),
      });
    } else {
      await createPromptPack({
        name: promptPackName.trim(),
        description: promptPackDescription.trim(),
        scope: "scene",
        promptPackType: "instruction",
        locked: false,
        scene: "course_content_generation",
        systemInstructions: promptPackInstructions.trim(),
        taskInstructions: "",
        priority: 100,
        enabled: true,
        nodeBindings: [],
      });
      setListPage(1);
    }
    setOpen(false);
    await reloadCurrentPage();
  }
  function editPromptPack(promptPack : PromptPack) {
    openEditPromptPack(promptPack);
  }
  async function togglePromptPack(promptPack : PromptPack, enabled: boolean) {
    await savePromptPackApi({ ...promptPack, enabled });
    await reloadCurrentPage();
  }
  async function addPromptPack() {
    await saveForm();
  }
  const viewingSystemPromptPack = Boolean(editingPromptPack?.locked);
  return (
    <div className="prompt-packs-page">
      <div className="settings-hero">
        <div>
          <span className="section-kicker">提示词包管理</span>
          <h2>文章生成提示词包</h2>
          <p>
            管理工作流节点调用 AI 时使用的协作规则。应用位置仅展示正式启用的已发布工作流；
            验收/自定义候选工作流不展示。请在工作流设计器中配置提示词包，发布后才会参与文章生成。
          </p>
        </div>
      </div>
      <div className="prompt-pack-toolbar">
        <div className="prompt-pack-search-box">
          <Search size={17} />
          <input
            aria-label="搜索 提示词包名称或内容"
            value={query}
            onChange={(event) => setQuery(event.target.value)}
            placeholder="搜索 提示词包名称或内容"
          />
          {query && (
            <button
              className="prompt-pack-search-clear"
              aria-label="清除 PromptPack 搜索"
              onClick={() => setQuery("")}
            >
              <X size={15} />
            </button>
          )}
        </div>
        <button onClick={openCreatePromptPack}>新增 PromptPack</button>
      </div>
      <div className="prompt-pack-table">
        <div className="prompt-pack-table-head">
          <span>名称</span>
          <span>功能简介</span>
          <span>应用位置</span>
          <span>状态</span>
          <span>操作</span>
        </div>
        {loading && promptPacks.length === 0 ? (
          <div className="prompt-pack-table-empty">正在加载提示词包…</div>
        ) : promptPacks.length === 0 ? (
          <div className="prompt-pack-table-empty">
            {total ? "没有符合条件的 PromptPack" : "还没有 PromptPack"}
          </div>
        ) : (
          promptPacks.map((promptPack) => (
            <div
              className={`prompt-pack-table-row ${promptPack.locked ? "system-prompt-pack-row" : ""}`}
              key={promptPack.id}
            >
              <strong>{promptPack.name}</strong>
              <span className="prompt-pack-description">
                {promptPack.description || "-"}
              </span>
              <PromptPackApplicationSummary
                applications={promptPack.applications}
                expanded={expandedApplicationIds.has(promptPack.id)}
                onToggle={() =>
                  setExpandedApplicationIds((current) => {
                    const next = new Set(current);
                    if (next.has(promptPack.id)) {
                      next.delete(promptPack.id);
                    } else {
                      next.add(promptPack.id);
                    }
                    return next;
                  })
                }
              />
              <button
                type="button"
                className="prompt-pack-toggle-button"
                aria-pressed={promptPack.enabled}
                disabled={promptPack.locked || loading}
                onClick={() => void togglePromptPack(promptPack, !promptPack.enabled)}
              >
                {promptPack.enabled ? "关闭" : "开启"}
              </button>
              <span className="prompt-pack-actions">
                {promptPack.locked ? (
                  <button
                    className="prompt-pack-view-button"
                    onClick={() => openEditPromptPack(promptPack)}
                  >
                    查看
                  </button>
                ) : (
                  <>
                    <button
                      className="prompt-pack-edit-button"
                      onClick={() => void editPromptPack(promptPack)}
                    >
                      编辑
                    </button>
                    <button
                      className="prompt-pack-delete-button"
                      disabled={loading}
                      onClick={async () => {
                        await deletePromptPack(promptPack.id);
                        await reloadCurrentPage();
                      }}
                    >
                      删除
                    </button>
                  </>
                )}
              </span>
            </div>
          ))
        )}
        {total > 0 ? (
          <nav className="source-management-pager prompt-pack-table-pager" aria-label="PromptPack 分页">
            <span className="source-management-pager-total">共 {total} 条</span>
            <div className="source-management-pager-controls">
              <button
                type="button"
                className="source-management-pager-nav"
                disabled={listPage <= 1 || loading}
                aria-label="上一页"
                onClick={() => setListPage((page) => Math.max(1, page - 1))}
              >
                <ChevronLeft size={16} />
                上一页
              </button>
              {listPageNumbers(listPage, listPageCount).map((token, index) =>
                token === "ellipsis" ? (
                  <span className="source-management-pager-ellipsis" key={`ellipsis-${index}`}>…</span>
                ) : (
                  <button
                    type="button"
                    key={token}
                    className={`source-management-pager-page${token === listPage ? " active" : ""}`}
                    disabled={loading}
                    aria-current={token === listPage ? "page" : undefined}
                    aria-label={`第 ${token} 页`}
                    onClick={() => setListPage(token)}
                  >
                    {token}
                  </button>
                ),
              )}
              <button
                type="button"
                className="source-management-pager-nav"
                disabled={listPage >= listPageCount || loading}
                aria-label="下一页"
                onClick={() =>
                  setListPage((page) => Math.min(listPageCount, page + 1))
                }
              >
                下一页
                <ChevronRight size={16} />
              </button>
            </div>
            <div className="source-management-pager-size" ref={pageSizeRef}>
              <span>每页</span>
              <button
                type="button"
                className={`source-management-pager-size-trigger${pageSizeOpen ? " open" : ""}`}
                disabled={loading}
                aria-label="每页条数"
                aria-haspopup="listbox"
                aria-expanded={pageSizeOpen}
                onClick={() => setPageSizeOpen((openMenu) => !openMenu)}
              >
                <em>{pageSize} 条</em>
                <ChevronDown size={14} />
              </button>
              {pageSizeOpen ? (
                <ul className="source-management-pager-size-menu" role="listbox" aria-label="可选每页条数">
                  {LIST_PAGE_SIZE_OPTIONS.map((size) => {
                    const active = size === pageSize;
                    return (
                      <li key={size} role="option" aria-selected={active}>
                        <button
                          type="button"
                          className={active ? "active" : undefined}
                          onClick={() => {
                            setPageSize(size);
                            setListPage(1);
                            setPageSizeOpen(false);
                          }}
                        >
                          <span>{size} 条</span>
                          {active ? <Check size={14} /> : null}
                        </button>
                      </li>
                    );
                  })}
                </ul>
              ) : null}
            </div>
          </nav>
        ) : null}
      </div>
      {open && (
        <div className="modal-backdrop" onClick={() => setOpen(false)}>
          <section
            className="prompt-pack-modal"
            role="dialog"
            aria-modal="true"
            aria-labelledby="prompt-pack-dialog-title"
            onClick={(event) => event.stopPropagation()}
          >
            <header className="prompt-pack-modal-header">
              <div>
                <span>{viewingSystemPromptPack ? "系统级能力" : "PromptPack 内容"}</span>
                <h3 id="prompt-pack-dialog-title">
                  {viewingSystemPromptPack
                    ? "查看系统级 PromptPack"
                    : editingPromptPack
                      ? "编辑 PromptPack"
                      : "新增 PromptPack"}
                </h3>
              </div>
              <button
                className="prompt-pack-modal-close"
                aria-label="关闭"
                onClick={() => setOpen(false)}
              >
                ×
              </button>
            </header>
            <div className="prompt-pack-modal-body">
              <label className="prompt-pack-field prompt-pack-field-full">
                <span>名称</span>
                <input
                  value={promptPackName}
                  readOnly={viewingSystemPromptPack}
                  onChange={(event) => setPromptPackName(event.target.value)}
                  placeholder="例如：知识库写作规范"
                />
              </label>
              <label className="prompt-pack-field prompt-pack-field-full">
                <span>功能简介</span>
                <input
                  value={promptPackDescription}
                  readOnly={viewingSystemPromptPack}
                  onChange={(event) => setPromptPackDescription(event.target.value)}
                  placeholder="简短描述该 PromptPack 的作用"
                  maxLength={120}
                />
              </label>
              <label className="prompt-pack-field prompt-pack-field-full">
                <span>内容</span>
                <textarea
                  value={promptPackInstructions}
                  readOnly={viewingSystemPromptPack}
                  onChange={(event) => setPromptPackInstructions(event.target.value)}
                  rows={7}
                  placeholder="输入文本 AI 在生成文章时需要遵循的规则"
                />
                <small>{promptPackInstructions.length} 字</small>
              </label>
              {formError && <p className="prompt-pack-form-error">{formError}</p>}
            </div>
            <footer className="modal-actions">
              <button onClick={() => setOpen(false)}>
                {viewingSystemPromptPack ? "关闭" : "取消"}
              </button>
              {!viewingSystemPromptPack && (
                <button
                  className="primary-button"
                  onClick={() => void addPromptPack()}
                >
                  保存 PromptPack
                </button>
              )}
            </footer>
          </section>
        </div>
      )}
    </div>
  );
}

/** 当前选中类型的供应商表单（文本 / 图片 / 多模态共用）。 */

export function ProviderCard({
  kind,
  title,
  badge,
  description,
  config,
  protocols,
  testing,
  testResult,
  onChange,
  onTest,
}: {
  kind: AiSettingsKind;
  title: string;
  badge: string;
  description: string;
  config: AiProviderConfig;
  protocols: ReadonlyArray<{ value: string; label: string }>;
  testing: boolean;
  testResult: {
    status: "idle" | "success" | "error";
    message: string;
    imagePreview?: string;
  };
  onChange: (
    key: "name" | "model" | "baseUrl" | "apiKey" | "protocol" | "imageQuality",
    value: string,
  ) => void;
  onTest: () => void;
}) {
  return (
    <article className={`provider-panel provider-${kind}`}>
      <div className="provider-panel-head">
        <div>
          <span className="provider-panel-badge">{badge}</span>
          <h3>{title}</h3>
          <p>{description}</p>
        </div>
        <button type="button" className="provider-test-btn" onClick={onTest} disabled={testing}>
          {testing ? "测试中..." : "测试连接"}
        </button>
      </div>
      <div className="config-fields">
        <label>
          <span>供应商名称</span>
          <input
            value={config.name}
            onChange={(event) => onChange("name", event.target.value)}
            placeholder="例如：OpenAI / Claude / 自定义中转"
          />
        </label>
        <label>
          <span>上游协议</span>
          <select
            value={config.protocol}
            onChange={(event) => onChange("protocol", event.target.value)}
          >
            {protocols.map((item) => (
              <option key={item.value} value={item.value}>
                {item.label}
              </option>
            ))}
          </select>
        </label>
        <label>
          <span>模型名称</span>
          <input
            value={config.model}
            onChange={(event) => onChange("model", event.target.value)}
            placeholder={
              kind === "text"
                ? "例如：gpt-5.4"
                : kind === "vision"
                  ? "例如：gpt-4o（需支持看图）"
                  : "例如：gpt-image-2 / flux / 自定义模型名"
            }
          />
        </label>
        {kind === "image" ? (
          <label>
            <span>图片质量</span>
            <select
              value={config.imageQuality || "medium"}
              onChange={(event) => onChange("imageQuality", event.target.value)}
            >
              <option value="low">low</option>
              <option value="medium">medium</option>
              <option value="high">high</option>
            </select>
          </label>
        ) : (
          <label className="config-field-spacer" aria-hidden="true" />
        )}
        <label className="config-field-wide">
          <span>请求 URL</span>
          <input
            value={config.baseUrl}
            onChange={(event) => onChange("baseUrl", event.target.value)}
            placeholder="例如：https://api.openai.com/v1"
          />
        </label>
        <label className="config-field-wide api-key-field">
          <span>API Key</span>
          <input
            type="password"
            value={config.apiKey}
            onChange={(event) => onChange("apiKey", event.target.value)}
            placeholder={
              config.apiKeyConfigured
                ? "已保存，留空则保持不变"
                : "输入 API Key"
            }
          />
        </label>
      </div>
      {kind === "image" && testResult.imagePreview && (
        <div className="image-test-preview">
          <img src={testResult.imagePreview} alt="图片 AI 连接测试结果" />
          <div>
            <strong>真实生成结果</strong>
            <small>
              该图片通过当前图片 AI 配置真实生成（模型：
              {config.model || "未设置"}
              ），测试可能产生少量 API 费用。
            </small>
          </div>
        </div>
      )}
      <p className={`connection-result ${testResult.status}`}>
        {testResult.message ||
          (kind === "image"
            ? "测试会真实生成一张图片，可能产生少量费用。"
            : kind === "vision"
              ? "测试会发送一张最小图片，模型需支持多模态输入。"
              : "配置以 API Key 保存，不需要 OAuth。切换上方类型可分别配置三种 AI。")}
      </p>
    </article>
  );
}
