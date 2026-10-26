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
  createSkill,
  deleteSkill,
  listSkills,
  pageSkills,
  updateSkill,
  updateSkill as saveSkillApi,
} from "../api";
import type { Skill } from "../api";
import {
  LIST_PAGE_SIZE_OPTIONS,
  listPageNumbers,
  type ListPageSize,
} from "../../../shared/api/pagination";
import { fetchBuiltinReady } from "../api";
import type { BuiltinReadyView } from "../api";

export function LayoutThemeTransfer() {
  const fileInput = useRef<HTMLInputElement | null>(null);
  const [message, setMessage] = useState("");

  async function exportThemes() {
    const themes = await listLayoutThemes();
    const payload = themes
      .filter((item) => item.type === "custom")
      .map(({ name, description, config }) => ({ name, description, config }));
    const url = URL.createObjectURL(
      new Blob([JSON.stringify(payload, null, 2)], {
        type: "application/json",
      }),
    );
    const anchor = document.createElement("a");
    anchor.href = url;
    anchor.download = "layout-themes.json";
    anchor.click();
    URL.revokeObjectURL(url);
  }

  async function importThemes(file: File | undefined) {
    if (!file) return;
    try {
      const parsed = JSON.parse(await file.text()) as Array<{
        name?: string;
        description?: string;
        config?: Record<string, unknown>;
      }>;
      if (!Array.isArray(parsed)) throw new Error("主题文件必须是数组格式");
      const entries = parsed.filter((item) => item.name?.trim());
      await Promise.all(
        entries.map((item) =>
          createLayoutTheme({
            name: item.name?.trim() || "未命名主题",
            description: item.description || "",
            config: item.config || {},
          }),
        ),
      );
      setMessage(`已导入 ${entries.length} 个主题，刷新页面后显示`);
    } catch (error) {
      setMessage(error instanceof Error ? error.message : "主题文件无效");
    } finally {
      if (fileInput.current) fileInput.current.value = "";
    }
  }

  return (
    <div className="layout-theme-transfer">
      <span>
        {message || "仅接受成文坊受控 JSON 字段，不导入 CSS 或脚本。"}
      </span>
      <button className="secondary-button" onClick={() => void exportThemes()}>
        <Download size={15} />
        导出主题
      </button>
      <button
        className="secondary-button"
        onClick={() => fileInput.current?.click()}
      >
        <Upload size={15} />
        导入主题
      </button>
      <input
        ref={fileInput}
        type="file"
        accept="application/json"
        hidden
        onChange={(event) => void importThemes(event.target.files?.[0])}
      />
    </div>
  );
}

/** Manage safe native publishing themes separately from AI provider settings. */

export function LayoutThemeSettings() {
  const [themes, setThemes] = useState<LayoutThemeData[]>([]);
  const [name, setName] = useState("");
  const [description, setDescription] = useState("");
  const [open, setOpen] = useState(false);
  const [editing, setEditing] = useState<LayoutThemeData | null>(null);
  const [config, setConfig] = useState<Record<string, unknown>>({});
  const [message, setMessage] = useState("");
  useEffect(() => {
    listLayoutThemes()
      .then(setThemes)
      .catch((error) =>
        setMessage(error instanceof Error ? error.message : "主题加载失败"),
      );
  }, []);
  async function clone(id: string) {
    try {
      const item = await cloneLayoutTheme(id);
      setThemes((items) => [...items, item]);
      setMessage("已创建可编辑副本");
    } catch (error) {
      setMessage(error instanceof Error ? error.message : "复制主题失败");
    }
  }
  function openCreate() {
    setEditing(null);
    setName("");
    setDescription("");
    setConfig({});
    setOpen(true);
  }
  function openEdit(item: LayoutThemeData) {
    setEditing(item);
    setName(item.name);
    setDescription(item.description);
    setConfig(item.config);
    setOpen(true);
  }
  async function saveTheme() {
    if (!name.trim()) return;
    try {
      const item = editing
        ? await updateLayoutTheme(editing.id, {
            name: name.trim(),
            description: description.trim(),
            config,
          })
        : await createLayoutTheme({
            name: name.trim(),
            description: description.trim(),
            config,
          });
      setThemes((items) =>
        editing
          ? items.map((current) => (current.id === item.id ? item : current))
          : [...items, item],
      );
      setOpen(false);
      setMessage(editing ? "主题已更新并生成新版本" : "主题已创建");
    } catch (error) {
      setMessage(error instanceof Error ? error.message : "主题保存失败");
    }
  }
  async function toggle(item: LayoutThemeData) {
    try {
      const updated = await setLayoutThemeStatus(
        item.id,
        item.status === "enabled" ? "disabled" : "enabled",
      );
      setThemes((items) =>
        items.map((current) => (current.id === updated.id ? updated : current)),
      );
    } catch (error) {
      setMessage(error instanceof Error ? error.message : "状态更新失败");
    }
  }
  async function remove(item: LayoutThemeData) {
    try {
      await deleteLayoutTheme(item.id);
      setThemes((items) => items.filter((current) => current.id !== item.id));
      setMessage("主题已删除");
    } catch (error) {
      setMessage(error instanceof Error ? error.message : "主题删除失败");
    }
  }
  function setConfigValue(key: string, value: string | number) {
    setConfig((current) => ({ ...current, [key]: value }));
  }
  return (
    <section className="settings-page publishing-settings">
      <div className="settings-hero">
        <div>
          <span className="section-kicker">Native publishing</span>
          <h2>排版主题</h2>
          <p>系统主题只读；复制后可维护自己的字号、颜色和阅读宽度。</p>
        </div>
        <button className="primary-button" onClick={openCreate}>
          新增主题
        </button>
      </div>
      {message && <div className="publishing-message">{message}</div>}
      <div className="layout-theme-list">
        <div className="layout-theme-head">
          <span>名称</span>
          <span>简介</span>
          <span>类型</span>
          <span>版本</span>
          <span>状态</span>
          <span>操作</span>
        </div>
        {themes.map((item) => (
          <div className="layout-theme-row" key={item.id}>
            <strong>{item.name}</strong>
            <span>{item.description || "未填写简介"}</span>
            <span>{item.type === "system" ? "系统" : "自定义"}</span>
            <span>V{item.versionNo}</span>
            <span
              className={`layout-theme-status ${
                item.status === "enabled" ? "is-enabled" : "is-disabled"
              }`}
            >
              {item.status === "enabled" ? "已启用" : "已停用"}
            </span>
            <div className="layout-theme-actions">
              {item.type === "system" ? (
                <button
                  className="secondary-button"
                  onClick={() => void clone(item.id)}
                >
                  复制
                </button>
              ) : (
                <>
                  <button
                    className="secondary-button"
                    onClick={() => openEdit(item)}
                  >
                    编辑
                  </button>
                  <button
                    className="secondary-button"
                    onClick={() => void toggle(item)}
                  >
                    {item.status === "enabled" ? "停用" : "启用"}
                  </button>
                  <button
                    className="layout-theme-delete"
                    onClick={() => void remove(item)}
                  >
                    删除
                  </button>
                </>
              )}
            </div>
          </div>
        ))}
      </div>
      {open && (
        <div
          className="modal-backdrop"
          role="presentation"
          onClick={() => setOpen(false)}
        >
          <div
            className="modal-card layout-theme-modal"
            role="dialog"
            aria-modal="true"
            aria-label={editing ? "编辑排版主题" : "新增排版主题"}
            onClick={(event) => event.stopPropagation()}
          >
            <div className="modal-header">
              <h3>{editing ? "编辑排版主题" : "新增排版主题"}</h3>
              <button
                className="icon-button"
                type="button"
                aria-label="关闭"
                onClick={() => setOpen(false)}
              >
                <X size={17} />
              </button>
            </div>
            <div className="theme-form-grid">
              <label>
                名称
                <input
                  value={name}
                  onChange={(event) => setName(event.target.value)}
                />
              </label>
              <label>
                简介
                <input
                  value={description}
                  onChange={(event) => setDescription(event.target.value)}
                />
              </label>
              <label>
                正文字号
                <input
                  type="number"
                  min="12"
                  max="24"
                  value={Number(config.fontSize ?? 16)}
                  onChange={(event) =>
                    setConfigValue("fontSize", Number(event.target.value))
                  }
                />
              </label>
              <label>
                行高
                <input
                  type="number"
                  min="1.2"
                  max="2.4"
                  step="0.1"
                  value={Number(config.lineHeight ?? 1.8)}
                  onChange={(event) =>
                    setConfigValue("lineHeight", Number(event.target.value))
                  }
                />
              </label>
              <label>
                内容宽度
                <input
                  type="number"
                  min="320"
                  max="900"
                  value={Number(config.contentWidth ?? 677)}
                  onChange={(event) =>
                    setConfigValue("contentWidth", Number(event.target.value))
                  }
                />
              </label>
              <label>
                段落间距
                <input
                  type="number"
                  min="0"
                  value={Number(config.paragraphSpacing ?? 16)}
                  onChange={(event) =>
                    setConfigValue(
                      "paragraphSpacing",
                      Number(event.target.value),
                    )
                  }
                />
              </label>
              <label>
                主色
                <input
                  type="color"
                  value={String(config.primaryColor ?? "#087ea4")}
                  onChange={(event) =>
                    setConfigValue("primaryColor", event.target.value)
                  }
                />
              </label>
              <label>
                强调背景
                <input
                  type="color"
                  value={String(config.accentColor ?? "#e8f5f8")}
                  onChange={(event) =>
                    setConfigValue("accentColor", event.target.value)
                  }
                />
              </label>
            </div>
            <div className="modal-actions">
              <button
                className="secondary-button"
                onClick={() => setOpen(false)}
              >
                取消
              </button>
              <button
                className="primary-button"
                onClick={() => void saveTheme()}
              >
                {editing ? "保存修改" : "创建"}
              </button>
            </div>
          </div>
        </div>
      )}
    </section>
  );
}

type SkillApplication = Skill["applications"][number];
