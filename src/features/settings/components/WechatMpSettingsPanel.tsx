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

export function WechatMpSettingsPanel() {
  const [draft, setDraft] = useState<WechatMpSettings>({
    accountName: "",
    appId: "",
    appSecret: "",
    appSecretConfigured: false,
    defaultAuthor: "",
    lastDraftMediaId: null,
    lastPushedAt: null,
    updatedAt: null,
  });
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [testing, setTesting] = useState(false);
  const [saveMessage, setSaveMessage] = useState("");
  const [testState, setTestState] = useState<{
    status: "idle" | "success" | "error";
    message: string;
  }>({ status: "idle", message: "" });

  useEffect(() => {
    let cancelled = false;
    loadWechatMpSettings()
      .then((settings) => {
        if (!cancelled) setDraft(settings);
      })
      .catch((error) => {
        if (!cancelled) {
          setSaveMessage(error instanceof Error ? error.message : "加载公众号配置失败");
        }
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, []);

  function updateField<K extends keyof WechatMpSettings>(key: K, value: WechatMpSettings[K]) {
    setDraft((current) => ({ ...current, [key]: value }));
    setTestState({ status: "idle", message: "配置已修改，请重新测试连接。" });
  }

  async function save() {
    setSaving(true);
    setSaveMessage("");
    try {
      const saved = await saveWechatMpSettings({
        accountName: draft.accountName,
        appId: draft.appId,
        appSecret: draft.appSecret,
        defaultAuthor: draft.defaultAuthor,
      });
      setDraft(saved);
      setSaveMessage(
        saved.updatedAt
          ? `已保存 · ${new Intl.DateTimeFormat("zh-CN", { dateStyle: "short", timeStyle: "medium" }).format(new Date(saved.updatedAt))}`
          : "已保存",
      );
    } catch (error) {
      setSaveMessage(error instanceof Error ? error.message : "配置保存失败");
    } finally {
      setSaving(false);
    }
  }

  async function test() {
    setTesting(true);
    setTestState({ status: "idle", message: "" });
    try {
      const result = await testWechatMpConnection({
        appId: draft.appId,
        appSecret: draft.appSecret,
      });
      setTestState({
        status: result.ok ? "success" : "error",
        message: result.message || (result.ok ? "连接成功" : "连接失败"),
      });
    } catch (error) {
      setTestState({
        status: "error",
        message: error instanceof Error ? error.message : "连接失败",
      });
    } finally {
      setTesting(false);
    }
  }

  if (loading) {
    return (
      <div className="wechat-mp-settings">
        <div className="settings-hero wechat-mp-hero">
          <p>正在加载公众号配置…</p>
        </div>
      </div>
    );
  }

  const statusMessage =
    testState.message ||
    (draft.appSecretConfigured
      ? "已配置凭证，可测试连接或推送草稿。"
      : "填写 AppID 与 AppSecret 后保存，再测试连接。");
  const lastPushedLabel = draft.lastPushedAt
    ? new Intl.DateTimeFormat("zh-CN", {
        dateStyle: "short",
        timeStyle: "short",
      }).format(new Date(draft.lastPushedAt))
    : "";

  return (
    <div className="wechat-mp-settings">
      <div className="settings-hero wechat-mp-hero">
        <div>
          <span className="section-kicker">WeChat Official Account</span>
          <h2>公众号配置</h2>
          <p>
            填写 AppID / AppSecret，将排版文章推送到公众号草稿箱。请先在公众平台将本机出口 IP
            加入白名单；成文坊只负责推送草稿，正式发布请到公众平台完成。
          </p>
        </div>
      </div>

      <div className="wechat-mp-layout">
        <article className="wechat-mp-card">
          <header className="wechat-mp-card-head">
            <div>
              <strong>{draft.accountName.trim() || "公众号账号"}</strong>
              <span>AppSecret 仅后端保存，接口不回传明文</span>
            </div>
          </header>

          <div className="wechat-mp-fields">
            <label>
              <span>账号备注名</span>
              <input
                value={draft.accountName}
                onChange={(event) => updateField("accountName", event.target.value)}
                placeholder="例如：主站公众号"
              />
            </label>
            <label>
              <span>默认作者</span>
              <input
                value={draft.defaultAuthor}
                onChange={(event) => updateField("defaultAuthor", event.target.value)}
                placeholder="推送草稿时的作者（≤16 字）"
                maxLength={16}
              />
            </label>
            <label>
              <span>AppID</span>
              <input
                value={draft.appId}
                onChange={(event) => updateField("appId", event.target.value)}
                placeholder="公众号 AppID"
                autoComplete="off"
              />
            </label>
            <label>
              <span>AppSecret</span>
              <input
                type="password"
                value={draft.appSecret}
                onChange={(event) => updateField("appSecret", event.target.value)}
                placeholder={
                  draft.appSecretConfigured ? "已保存，留空则保持不变" : "输入 AppSecret"
                }
                autoComplete="new-password"
              />
            </label>
          </div>

          <div className={`wechat-mp-status ${testState.status}`}>
            <p>{statusMessage}</p>
            {draft.lastDraftMediaId ? (
              <dl>
                <div>
                  <dt>最近草稿</dt>
                  <dd title={draft.lastDraftMediaId}>{draft.lastDraftMediaId}</dd>
                </div>
                {lastPushedLabel ? (
                  <div>
                    <dt>推送时间</dt>
                    <dd>{lastPushedLabel}</dd>
                  </div>
                ) : null}
              </dl>
            ) : null}
          </div>

          <footer className="wechat-mp-actions">
            <small>{saveMessage || "配置将保存到本地数据库"}</small>
            <div className="wechat-mp-action-buttons">
              <button
                type="button"
                className="wechat-mp-test-btn"
                onClick={() => void test()}
                disabled={testing || !draft.appId.trim()}
              >
                {testing ? "测试中…" : "测试连接"}
              </button>
              <button
                type="button"
                className="wechat-mp-save-btn"
                onClick={() => void save()}
                disabled={saving}
              >
                {saving ? "保存中…" : "保存配置"}
              </button>
            </div>
          </footer>
        </article>

        <aside className="wechat-mp-aside">
          <section>
            <h3>接入清单</h3>
            <ol>
              <li>在公众平台创建/选择账号，拿到 AppID 与 AppSecret</li>
              <li>把本机出口 IP 加入接口 IP 白名单</li>
              <li>在本页填写凭证并保存，再点「测试连接」</li>
              <li>排版完成后推送草稿，到公众平台确认发布</li>
            </ol>
          </section>
          <section>
            <h3>安全说明</h3>
            <ul>
              <li>AppSecret 只存后端数据库，列表接口不回传明文</li>
              <li>留空保存时保持原密钥不变，避免误覆盖</li>
              <li>成文坊不支持直接群发，仅写入草稿箱</li>
            </ul>
          </section>
          <section>
            <h3>当前状态</h3>
            <dl>
              <div>
                <dt>凭证</dt>
                <dd>{draft.appSecretConfigured ? "已配置" : "未配置"}</dd>
              </div>
              <div>
                <dt>AppID</dt>
                <dd>{draft.appId.trim() || "未填写"}</dd>
              </div>
              <div>
                <dt>最近推送</dt>
                <dd>{lastPushedLabel || "暂无"}</dd>
              </div>
            </dl>
          </section>
        </aside>
      </div>
    </div>
  );
}

/** Import and export controlled theme JSON without exposing arbitrary CSS. */
