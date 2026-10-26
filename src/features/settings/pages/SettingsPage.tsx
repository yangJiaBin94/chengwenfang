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
import {
  SystemReadyPanel,
} from "../components/SystemReadyPanel";
import {
  WechatMpSettingsPanel,
} from "../components/WechatMpSettingsPanel";
import {
  LayoutThemeTransfer,
  LayoutThemeSettings,
} from "../components/LayoutThemeSettings";
import {
  SkillSettingsPanel,
  ProviderCard,
} from "../components/SkillSettingsPanel";


export function AiSettingsPage({
  settings,
  updatedAt,
  onSave,
  initialSection = null,
}: {
  settings: AiSettings;
  updatedAt: string;
  onSave: (settings: AiSettings) => Promise<string>;
  initialSection?: "ai" | "skills" | "layoutThemes" | "wechatMp" | "system" | null;
}) {
  const [settingsSection, setSettingsSection] = useState<
    "ai" | "skills" | "layoutThemes" | "wechatMp" | "system"
  >(initialSection || "ai");
  useEffect(() => {
    if (initialSection) setSettingsSection(initialSection);
  }, [initialSection]);
  const [draft, setDraft] = useState(settings);
  const [activeKind, setActiveKind] = useState<AiSettingsKind>("text");
  const [testState, setTestState] = useState<
    Record<
      AiSettingsKind,
      {
        status: "idle" | "success" | "error";
        message: string;
        imagePreview?: string;
      }
    >
  >({
    text: { status: "idle", message: "" },
    image: { status: "idle", message: "" },
    vision: { status: "idle", message: "" },
  });
  const [saveMessage, setSaveMessage] = useState("");
  const [saving, setSaving] = useState(false);
  const [testing, setTesting] = useState<AiSettingsKind | null>(null);
  const [skills, setSkills] = useState<Skill[]>([]);
  const [skillName, setSkillName] = useState("");
  const [skillInstructions, setSkillInstructions] = useState("");
  const [skillScene, setSkillScene] = useState("course_content_generation");
  const [skillPriority, setSkillPriority] = useState(100);
  const [skillEnabled, setSkillEnabled] = useState(true);
  useEffect(() => {
    listSkills()
      .then(setSkills)
      .catch(() => undefined);
  }, []);
  async function addSkill() {
    if (!skillName.trim() || !skillInstructions.trim()) return;
    const item = await createSkill({
      name: skillName.trim(),
      description: "",
      scope: "scene",
      skillType: "instruction",
      locked: false,
      scene: skillScene,
      systemInstructions: skillInstructions.trim(),
      taskInstructions: "",
      priority: skillPriority,
      enabled: skillEnabled,
    });
    setSkills((items) =>
      [...items, item].sort((a, b) => a.priority - b.priority),
    );
    setSkillName("");
    setSkillInstructions("");
    setSkillPriority(100);
    setSkillEnabled(true);
  }

  useEffect(() => setDraft(settings), [settings]);

  /** Update one draft provider field and invalidate its previous test result. */
  function update(
    kind: AiSettingsKind,
    key: "name" | "model" | "baseUrl" | "apiKey" | "protocol" | "imageQuality",
    value: string,
  ) {
    // Any edited field invalidates the previous connection-test result.
    setDraft((current) => ({
      ...current,
      [kind]: { ...current[kind], [key]: value },
    }));
    setTestState((current) => ({
      ...current,
      [kind]: { status: "idle", message: "配置已修改，请重新测试连接。" },
    }));
  }

  /** Perform a real connection test for the selected provider card. */
  async function test(kind: AiSettingsKind) {
    // Tests use draft form values and perform a real upstream request.
    setTesting(kind);
    setTestState((current) => ({
      ...current,
      [kind]: { status: "idle", message: "" },
    }));
    try {
      const result = await testAiConnection(draft[kind], kind);
      setTestState((current) => ({
        ...current,
        [kind]: {
          status: "success",
          message: result.message,
          imagePreview: result.imagePreview,
        },
      }));
    } catch (error) {
      setTestState((current) => ({
        ...current,
        [kind]: {
          status: "error",
          message: error instanceof Error ? error.message : "连接失败",
        },
      }));
    } finally {
      setTesting(null);
    }
  }

  /** Persist both provider cards and show the committed database time. */
  async function save() {
    // Persist both provider cards as one backend settings update.
    setSaving(true);
    setSaveMessage("");
    try {
      const savedAt = await onSave(draft);
      setSaveMessage(
        `已持久化到本地数据库 · ${new Intl.DateTimeFormat("zh-CN", { dateStyle: "short", timeStyle: "medium" }).format(new Date(savedAt))}`,
      );
    } catch (error) {
      setSaveMessage(error instanceof Error ? error.message : "配置保存失败");
    } finally {
      setSaving(false);
    }
  }

  return (
    <section className="settings-page">
      <nav className="settings-subnav" aria-label="设置菜单">
        <button
          className={settingsSection === "ai" ? "active" : ""}
          onClick={() => setSettingsSection("ai")}
        >
          AI 配置
        </button>
        <button
          className={settingsSection === "skills" ? "active" : ""}
          onClick={() => setSettingsSection("skills")}
        >
          Skill 配置管理
        </button>
        <button
          className={settingsSection === "layoutThemes" ? "active" : ""}
          onClick={() => setSettingsSection("layoutThemes")}
        >
          排版主题
        </button>
        <button
          className={settingsSection === "wechatMp" ? "active" : ""}
          onClick={() => setSettingsSection("wechatMp")}
        >
          公众号配置
        </button>
        <button
          className={settingsSection === "system" ? "active" : ""}
          onClick={() => setSettingsSection("system")}
        >
          系统就绪
        </button>
      </nav>
      {settingsSection === "layoutThemes" && <LayoutThemeTransfer />}
      {settingsSection === "layoutThemes" ? (
        <LayoutThemeSettings />
      ) : settingsSection === "skills" ? (
        <SkillSettingsPanel />
      ) : settingsSection === "wechatMp" ? (
        <WechatMpSettingsPanel />
      ) : settingsSection === "system" ? (
        <SystemReadyPanel />
      ) : (
        <>
          <div className="settings-hero">
            <div>
              <span className="section-kicker">Provider Configuration</span>
              <h2>AI 供应商配置</h2>
              <p>
                文本、图片与多模态使用独立供应商。成文坊会根据处理场景自动选择对应
                AI，不混用配置。
              </p>
            </div>
            <div className="save-settings-wrap">
              <small>
                {saveMessage ||
                  (updatedAt
                    ? `数据库最近保存：${new Intl.DateTimeFormat("zh-CN", { dateStyle: "short", timeStyle: "short" }).format(new Date(updatedAt))}`
                    : "配置将保存到本地 PostgreSQL 数据库")}
              </small>
              <button
                className="save-settings"
                onClick={save}
                disabled={saving}
              >
                {saving ? "保存中..." : "保存全部配置"}
              </button>
            </div>
          </div>

          <div className="provider-workspace">
            <div className="provider-tabs" role="tablist" aria-label="AI 类型">
              {(
                [
                  {
                    kind: "text" as const,
                    title: "文本 AI",
                    hint: "生成 · 审核 · 修复 · 语音总结",
                    icon: <FileText size={16} />,
                  },
                  {
                    kind: "image" as const,
                    title: "图片 AI",
                    hint: "插图 · 封面 · 视觉资产",
                    icon: <Image size={16} />,
                  },
                  {
                    kind: "vision" as const,
                    title: "多模态 AI",
                    hint: "视频画面理解",
                    icon: <Eye size={16} />,
                  },
                ] as const
              ).map((tab) => {
                const configured = !!draft[tab.kind].apiKeyConfigured;
                const active = activeKind === tab.kind;
                return (
                  <button
                    key={tab.kind}
                    type="button"
                    role="tab"
                    aria-selected={active}
                    className={`provider-tab provider-tab-${tab.kind}${active ? " is-active" : ""}`}
                    onClick={() => setActiveKind(tab.kind)}
                  >
                    <span className="provider-tab-icon">{tab.icon}</span>
                    <span className="provider-tab-copy">
                      <strong>{tab.title}</strong>
                      <small>{tab.hint}</small>
                    </span>
                    <span
                      className={`provider-tab-status${configured ? " is-ready" : ""}`}
                    >
                      {configured ? "已配置" : "未配置"}
                    </span>
                  </button>
                );
              })}
            </div>

            <ProviderCard
              kind={activeKind}
              title={
                activeKind === "text"
                  ? "文本 AI"
                  : activeKind === "image"
                    ? "图片 AI"
                    : "多模态 AI"
              }
              badge={
                activeKind === "text"
                  ? "文本处理"
                  : activeKind === "image"
                    ? "图片处理"
                    : "看图理解"
              }
              description={
                activeKind === "text"
                  ? "课程原稿、公众号文章、AI 审核/修复，以及有口播视频的文本总结"
                  : activeKind === "image"
                    ? "课程插图、公众号封面和配套视觉资产"
                    : "无口播/动画视频的关键帧理解与画面要点总结"
              }
              config={draft[activeKind]}
              protocols={
                activeKind === "text"
                  ? textProtocolOptions
                  : activeKind === "image"
                    ? imageProtocolOptions
                    : visionProtocolOptions
              }
              testing={testing === activeKind}
              testResult={testState[activeKind]}
              onChange={(key, value) => update(activeKind, key, value)}
              onTest={() => test(activeKind)}
            />
          </div>
          <div className="skills-settings">
            <div className="skills-settings-head">
              <strong>文章生成 Skill</strong>
              <span>
                仅已发布工作流节点上的 Skill 会参与生成；请在工作流设计器配置并发布。
              </span>
            </div>
            <div className="skill-create">
              <input
                value={skillName}
                onChange={(event) => setSkillName(event.target.value)}
                placeholder="Skill 名称"
              />
              <input
                value={skillInstructions}
                onChange={(event) => setSkillInstructions(event.target.value)}
                placeholder="协作规则"
              />
              <button onClick={() => void addSkill()}>新增</button>
            </div>
            {skills.map((skill) => (
              <div className="skill-row" key={skill.id}>
                <input
                  value={skill.name}
                  onChange={(event) =>
                    setSkills((items) =>
                      items.map((item) =>
                        item.id === skill.id
                          ? { ...item, name: event.target.value }
                          : item,
                      ),
                    )
                  }
                />
                <input
                  type="number"
                  min="0"
                  value={skill.priority}
                  onChange={(event) =>
                    setSkills((items) =>
                      items.map((item) =>
                        item.id === skill.id
                          ? { ...item, priority: Number(event.target.value) }
                          : item,
                      ),
                    )
                  }
                />
                <label>
                  <input
                    type="checkbox"
                    checked={skill.enabled}
                    onChange={(event) =>
                      setSkills((items) =>
                        items.map((item) =>
                          item.id === skill.id
                            ? { ...item, enabled: event.target.checked }
                            : item,
                        ),
                      )
                    }
                  />
                  启用
                </label>
                <button onClick={() => void updateSkill(skill)}>保存</button>
                <button
                  onClick={async () => {
                    await deleteSkill(skill.id);
                    setSkills((items) =>
                      items.filter((item) => item.id !== skill.id),
                    );
                  }}
                >
                  删除
                </button>
              </div>
            ))}
          </div>
        </>
      )}
    </section>
  );
}

/** 设置页「公众号配置」：AppID / AppSecret 与连通性探测。 */
/** 系统就绪：内置 Skill / 工作流体检。 */


