import { useEffect, useState } from "react";
import { fetchBuiltinReady } from "../api";
import type {
  BuiltinReadyView,
  SystemReadinessItem,
  SystemReadinessSection,
  SystemReadinessStatus,
} from "../api";

const SECTIONS: { id: SystemReadinessSection; title: string; hint: string }[] = [
  {
    id: "default",
    title: "课程 + 知识库",
    hint: "这些项都通过后，默认档才算就绪。密钥要在设置里自己填，种子不会写入。",
  },
  {
    id: "hotnews",
    title: "热点加装",
    hint: "未加装会少雷达搜索、JS 页正文或截图，不是课程和知识库坏了。满血启动会装上三条 CLI，并拉起 SearXNG / Lightpanda。",
  },
  {
    id: "optional",
    title: "可选能力",
    hint: "Graph 与 Agent 默认关闭。Neo4j 容器可以随满血档起来，应用开关不会因此打开。",
  },
];

function statusLabel(status: SystemReadinessStatus): string {
  if (status === "pass") return "通过";
  if (status === "fail") return "未通过";
  if (status === "not_installed") return "未加装";
  return "默认关闭";
}

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
          <p>正在检查系统就绪…</p>
        </div>
      </div>
    );
  }

  const checks = view?.checks ?? [];

  return (
    <div className="system-ready-panel">
      <div className="settings-hero">
        <div>
          <span className="section-kicker">System Readiness</span>
          <h2>系统就绪</h2>
          <p>
            需登录使用；默认种子管理员请改密。不要把 5173 / 8001 裸暴露到公网。默认档是课程 + 知识库
            （MySQL、Redis、Qdrant）。空库启动会自动导入提示词包和三条主工作流。
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
            {view.ready
              ? "通过：课程 + 知识库已就绪"
              : "未通过：课程 + 知识库还有必需项。热点未加装不会单独导致这里失败。"}
          </div>
          <dl className="system-ready-summary">
            <div>
              <dt>提示词包</dt>
              <dd>
                {view.promptPackCount} / 期望 {view.expectedPromptPackCount}
              </dd>
            </div>
            <div>
              <dt>工作流</dt>
              <dd>{view.workflowReady ? "就绪" : "未就绪"}</dd>
            </div>
          </dl>
          {SECTIONS.map((section) => {
            const rows = checks.filter((check) => check.section === section.id);
            if (rows.length === 0) return null;
            return (
              <section className="system-ready-group" key={section.id}>
                <h3>{section.title}</h3>
                <p>{section.hint}</p>
                <ul>
                  {rows.map((check) => (
                    <CheckRow key={check.key} check={check} />
                  ))}
                </ul>
              </section>
            );
          })}
          {view.issues.length > 0 ? (
            <section className="system-ready-issues">
              <h3>还需处理</h3>
              <ul>
                {view.issues.map((issue) => (
                  <li key={issue}>{issue}</li>
                ))}
              </ul>
            </section>
          ) : null}
          {!view.ready && view.bootstrapHint ? (
            <section className="system-ready-hint">
              <h3>补种</h3>
              <code>{view.bootstrapHint}</code>
            </section>
          ) : null}
        </>
      ) : null}
    </div>
  );
}

function CheckRow({ check }: { check: SystemReadinessItem }) {
  return (
    <li className={`system-ready-check is-${check.status}`}>
      <span className="system-ready-check-label">{check.label}</span>
      <span className={`system-ready-chip is-${check.status}`}>{statusLabel(check.status)}</span>
      <span className="system-ready-check-detail">{check.detail}</span>
    </li>
  );
}
