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

export function SystemReadyPanel() {
  const [view, setView] = useState<BuiltinReadyView | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [refreshing, setRefreshing] = useState(false);

  async function load() {
    setError("");
    try {
      const result = await fetchBuiltinReady();
      setView(result);
    } catch (loadError) {
      setError(loadError instanceof Error ? loadError.message : "加载系统就绪状态失败");
    } finally {
      setLoading(false);
      setRefreshing(false);
    }
  }

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    fetchBuiltinReady()
      .then((result) => {
        if (!cancelled) setView(result);
      })
      .catch((loadError) => {
        if (!cancelled) {
          setError(loadError instanceof Error ? loadError.message : "加载系统就绪状态失败");
        }
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, []);

  async function refresh() {
    setRefreshing(true);
    await load();
  }

  if (loading) {
    return (
      <div className="system-ready-panel">
        <div className="settings-hero">
          <p>正在检查内置配置…</p>
        </div>
      </div>
    );
  }

  return (
    <div className="system-ready-panel">
      <div className="settings-hero">
        <div>
          <span className="section-kicker">System Readiness</span>
          <h2>系统就绪</h2>
          <p>
            检查库内内置 Skill 与三条主工作流是否已种子化并可生成。缺项时请按提示执行
            bootstrap 脚本。
          </p>
        </div>
        <div className="save-settings-wrap">
          <button className="save-settings" onClick={refresh} disabled={refreshing}>
            {refreshing ? "刷新中…" : "重新检查"}
          </button>
        </div>
      </div>

      {error ? (
        <div className="system-ready-status is-error" role="alert">
          {error}
        </div>
      ) : view ? (
        <>
          <div
            className={`system-ready-status ${view.ready ? "is-ok" : "is-error"}`}
            role="status"
          >
            {view.ready ? "通过：内置配置已就绪" : "未通过：存在缺失或不可用项"}
          </div>
          <dl className="system-ready-summary">
            <div>
              <dt>Skill</dt>
              <dd>
                {view.skillCount} / 期望 {view.expectedSkillCount}
              </dd>
            </div>
            <div>
              <dt>工作流</dt>
              <dd>{view.workflowReady ? "就绪" : "未就绪"}</dd>
            </div>
          </dl>
          {view.issues.length > 0 ? (
            <section className="system-ready-issues">
              <h3>问题列表</h3>
              <ul>
                {view.issues.map((issue) => (
                  <li key={issue}>{issue}</li>
                ))}
              </ul>
            </section>
          ) : null}
          {!view.ready && view.bootstrapHint ? (
            <section className="system-ready-hint">
              <h3>Bootstrap 提示</h3>
              <code>{view.bootstrapHint}</code>
            </section>
          ) : null}
        </>
      ) : null}
    </div>
  );
}
