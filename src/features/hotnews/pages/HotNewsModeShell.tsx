import type { Dispatch, SetStateAction } from "react";
import { Activity, CheckCircle2, ChevronDown } from "lucide-react";
import {
  deleteContentTheme,
  hotNewsThemePath,
  patchContentTheme,
} from "../../course/api";
import type { ContentThemeData } from "../../course/api";
import { HotNewsPage } from "./HotNewsPage";
import { HotNewsArticleWorkspace } from "./HotNewsArticleWorkspace";
import { HotNewsExecutionPage } from "./HotNewsExecutionPage";
import { HotNewsRadarCollectExecutionPage } from "./HotNewsRadarCollectExecutionPage";
import { HotNewsResearchPage } from "./HotNewsResearchPage";
import { HotNewsSelectedPage } from "./HotNewsSelectedPage";
import { HotNewsThemeWizard } from "./HotNewsThemeWizard";
import { SourceManagementPage } from "./SourceManagementPage";

/** Props for the hot-news mode shell (overview, switcher, child routes). */
export type HotNewsModeShellProps = {
  hotNewsThemeWizardOpen: boolean;
  hotNewsThemeWizardResumeId: string | null;
  setHotNewsThemeWizardOpen: (open: boolean) => void;
  setHotNewsThemeWizardResumeId: (id: string | null) => void;
  showNotice: (title: string, message: string) => void;
  navigate: (path: string) => void;
  confirmAction: (
    title: string,
    message: string,
    tone?: "default" | "danger",
  ) => Promise<boolean>;
  promptAction: (
    title: string,
    message: string,
    options?: {
      inputLabel?: string;
      placeholder?: string;
      minLength?: number;
      tone?: "default" | "danger";
    },
  ) => Promise<string | null>;
  setHotNewsThemes: Dispatch<SetStateAction<ContentThemeData[]>>;
  setSelectedHotNewsThemeId: (id: string) => void;
  refreshCourseSystemThemes: () => void | Promise<void>;
  routeHotNewsRadar: boolean;
  routeHotNewsSelected: boolean;
  routeHotNewsSourceManagement: boolean;
  selectedHotNewsThemeMeta: ContentThemeData | null | undefined;
  selectedHotNewsThemeId: string | null;
  hotNewsThemePickerOpen: boolean;
  setHotNewsThemePickerOpen: Dispatch<SetStateAction<boolean>>;
  hotNewsThemes: ContentThemeData[];
  hotNewsDefaultSearchQueries: string;
  hotNewsDefaultGithubQuery: string;
  hotNewsThemeSlug: string;
  routeHotNewsCollectThemeId: string | null;
  collectHotNewsThemeSlug: string;
  collectHotNewsThemeMeta: ContentThemeData | null | undefined;
  routeHotNewsExecutionArticleId: string | null;
  routeHotNewsArticleId: string | null;
  routeHotNewsResearchTopicId: string | null;
};

/**
 * Hot-news mode shell: theme wizard, overview grid, theme switcher,
 * and mounts for radar / selected / sources / execution / research child pages.
 */
export function HotNewsModeShell({
  hotNewsThemeWizardOpen,
  hotNewsThemeWizardResumeId,
  setHotNewsThemeWizardOpen,
  setHotNewsThemeWizardResumeId,
  showNotice,
  navigate,
  confirmAction,
  promptAction,
  setHotNewsThemes,
  setSelectedHotNewsThemeId,
  refreshCourseSystemThemes,
  routeHotNewsRadar,
  routeHotNewsSelected,
  routeHotNewsSourceManagement,
  selectedHotNewsThemeMeta,
  selectedHotNewsThemeId,
  hotNewsThemePickerOpen,
  setHotNewsThemePickerOpen,
  hotNewsThemes,
  hotNewsDefaultSearchQueries,
  hotNewsDefaultGithubQuery,
  hotNewsThemeSlug,
  routeHotNewsCollectThemeId,
  collectHotNewsThemeSlug,
  collectHotNewsThemeMeta,
  routeHotNewsExecutionArticleId,
  routeHotNewsArticleId,
  routeHotNewsResearchTopicId,
}: HotNewsModeShellProps) {
  if (hotNewsThemeWizardOpen) {
    return (
      <HotNewsThemeWizard
        resumeThemeIdOrCode={hotNewsThemeWizardResumeId}
        showNotice={showNotice}
        navigate={navigate}
        onCancel={() => {
          setHotNewsThemeWizardOpen(false);
          setHotNewsThemeWizardResumeId(null);
          void navigate("/modes/hot-news");
        }}
        onCommitted={async (theme) => {
          setHotNewsThemeWizardOpen(false);
          setHotNewsThemeWizardResumeId(null);
          // 先写入本地启用态，再回主题总览（勿进雷达：同步路由时列表可能仍是 draft 会被送回向导）
          setHotNewsThemes((current) => {
            const without = current.filter((item) => item.id !== theme.id);
            return [theme, ...without];
          });
          setSelectedHotNewsThemeId(theme.id);
          void navigate("/modes/hot-news");
          await refreshCourseSystemThemes();
        }}
      />
    );
  }

  if (
    (routeHotNewsRadar ||
      routeHotNewsSelected ||
      routeHotNewsSourceManagement) &&
    selectedHotNewsThemeMeta?.status === "enabled"
  ) {
    return (
      <>
        <div className="theme-switcher">
          <button
            className="theme-switcher-button"
            type="button"
            aria-expanded={hotNewsThemePickerOpen}
            onClick={() => setHotNewsThemePickerOpen((open) => !open)}
          >
            <span>{selectedHotNewsThemeMeta.name}</span>
            <ChevronDown size={14} />
          </button>
          {hotNewsThemePickerOpen && (
            <div className="theme-switcher-menu">
              {hotNewsThemes
                .filter((theme) => theme.status === "enabled")
                .map((theme) => (
                  <button
                    className={
                      theme.id === selectedHotNewsThemeId ? "selected" : ""
                    }
                    key={theme.id}
                    type="button"
                    onClick={() => {
                      const view = routeHotNewsSourceManagement
                        ? "sources"
                        : routeHotNewsSelected
                          ? "selected"
                          : "radar";
                      setSelectedHotNewsThemeId(theme.id);
                      setHotNewsThemePickerOpen(false);
                      void navigate(hotNewsThemePath(theme.code, view));
                    }}
                  >
                    <span>{theme.name}</span>
                    {theme.id === selectedHotNewsThemeId && (
                      <CheckCircle2 size={15} />
                    )}
                  </button>
                ))}
            </div>
          )}
        </div>
        {routeHotNewsSourceManagement ? (
          <SourceManagementPage
            themeId={selectedHotNewsThemeMeta.id}
            themeName={selectedHotNewsThemeMeta.name}
            defaultSearchQueries={hotNewsDefaultSearchQueries}
            defaultGithubQuery={hotNewsDefaultGithubQuery}
            showNotice={showNotice}
            confirmAction={confirmAction}
          />
        ) : routeHotNewsSelected ? (
          <HotNewsSelectedPage
            themeId={selectedHotNewsThemeMeta.id}
            themeName={selectedHotNewsThemeMeta.name}
            showNotice={showNotice}
            navigate={navigate}
          />
        ) : (
          <HotNewsPage
            themeId={selectedHotNewsThemeMeta.id}
            themeSlug={hotNewsThemeSlug}
            themeName={selectedHotNewsThemeMeta.name}
            showNotice={showNotice}
            navigate={navigate}
          />
        )}
      </>
    );
  }

  if (routeHotNewsCollectThemeId) {
    return (
      <HotNewsRadarCollectExecutionPage
        themeId={routeHotNewsCollectThemeId}
        themeSlug={collectHotNewsThemeSlug}
        themeName={
          collectHotNewsThemeMeta?.name ||
          selectedHotNewsThemeMeta?.name ||
          "热点雷达采集"
        }
        navigate={navigate}
      />
    );
  }

  if (routeHotNewsExecutionArticleId) {
    return (
      <HotNewsExecutionPage
        articleId={routeHotNewsExecutionArticleId}
        navigate={navigate}
      />
    );
  }

  if (routeHotNewsArticleId) {
    return (
      <HotNewsArticleWorkspace
        articleId={routeHotNewsArticleId}
        showNotice={showNotice}
        confirmAction={confirmAction}
        promptAction={promptAction}
        navigate={navigate}
      />
    );
  }

  if (routeHotNewsResearchTopicId) {
    return (
      <HotNewsResearchPage
        topicId={routeHotNewsResearchTopicId}
        themeSlug={hotNewsThemeSlug}
        showNotice={showNotice}
        confirmAction={confirmAction}
        navigate={navigate}
      />
    );
  }

  return (
    <section className="mode-overview hot-news-mode-overview">
      <div className="context-line">热点资讯模式</div>
      <header>
        <span className="section-kicker">Hot News Mode</span>
        <h2>热点资讯模式</h2>
        <p>
          从多来源发现热点，经过聚合、评分和人工确认，沉淀为值得深入写作的选题。
        </p>
      </header>
      <div className="theme-section">
        <div className="theme-section-head">
          <h3>主题</h3>
          <div className="theme-section-actions">
            <span>{hotNewsThemes.length} 个主题</span>
            <button
              className="primary-button"
              type="button"
              onClick={() => {
                setHotNewsThemeWizardResumeId(null);
                setHotNewsThemeWizardOpen(true);
                void navigate("/modes/hot-news/themes/new");
              }}
            >
              新建主题
            </button>
          </div>
        </div>
        <div className="theme-card-grid">
          {hotNewsThemes.length === 0 ? (
            <p className="empty-hint">
              还没有主题，点击「新建主题」开始配置雷达。
            </p>
          ) : (
            hotNewsThemes.map((theme) => (
              <div className="theme-card-wrap" key={theme.id}>
                <button
                  className={`theme-card hot-news-theme-card${theme.status === "draft" ? " is-draft" : ""}`}
                  type="button"
                  onClick={() => {
                    if (theme.status === "draft") {
                      setHotNewsThemeWizardResumeId(theme.id);
                      setHotNewsThemeWizardOpen(true);
                      void navigate(hotNewsThemePath(theme.code, "wizard"));
                      return;
                    }
                    if (theme.status !== "enabled") {
                      showNotice(
                        "主题已停用",
                        "请先启用后再进入雷达与采集。",
                      );
                      return;
                    }
                    setSelectedHotNewsThemeId(theme.id);
                    void navigate(hotNewsThemePath(theme.code, "radar"));
                  }}
                >
                  <span className="theme-card-icon">
                    <Activity size={22} />
                  </span>
                  <span className="theme-card-copy">
                    <strong>
                      {theme.name}
                      {theme.status === "draft" ? (
                        <em className="theme-card-status-badge">草稿</em>
                      ) : null}
                    </strong>
                    <small>
                      {theme.description ||
                        (theme.status === "enabled"
                          ? "已启用"
                          : theme.status === "disabled"
                            ? "已停用"
                            : "创建未完成，点击继续配置")}
                    </small>
                  </span>
                  <span className="theme-card-stats">
                    <span>
                      <b>{theme.itemCount ?? 0}</b>
                      <small>来源条目</small>
                    </span>
                    <span>
                      <b>{theme.candidateCount ?? 0}</b>
                      <small>候选选题</small>
                    </span>
                  </span>
                </button>
                <div className="theme-card-menu">
                  {theme.status === "draft" ? (
                    <>
                      <button
                        type="button"
                        onClick={() => {
                          setHotNewsThemeWizardResumeId(theme.id);
                          setHotNewsThemeWizardOpen(true);
                          void navigate(hotNewsThemePath(theme.code, "wizard"));
                        }}
                      >
                        继续创建
                      </button>
                      <button
                        type="button"
                        onClick={() => {
                          void (async () => {
                            const ok = await confirmAction(
                              "删除草稿主题？",
                              `「${theme.name}」尚未启用，删除后可在回收站恢复。`,
                              "danger",
                            );
                            if (!ok) return;
                            try {
                              await deleteContentTheme(theme.id);
                              showNotice("草稿已删除", "可在回收站恢复。");
                              await refreshCourseSystemThemes();
                            } catch (error) {
                              showNotice(
                                "删除失败",
                                error instanceof Error
                                  ? error.message
                                  : "请稍后重试",
                              );
                            }
                          })();
                        }}
                      >
                        删除
                      </button>
                    </>
                  ) : null}
                  {(theme.status === "enabled" ||
                    theme.status === "disabled") && (
                    <>
                      <button
                        type="button"
                        onClick={() => {
                          const next =
                            theme.status === "enabled" ? "disabled" : "enabled";
                          void patchContentTheme(theme.id, { status: next })
                            .then(async () => {
                              showNotice(
                                next === "enabled" ? "主题已启用" : "主题已停用",
                                theme.name,
                              );
                              await refreshCourseSystemThemes();
                            })
                            .catch((error) => {
                              showNotice(
                                "更新失败",
                                error instanceof Error
                                  ? error.message
                                  : "请稍后重试",
                              );
                            });
                        }}
                      >
                        {theme.status === "enabled" ? "停用" : "启用"}
                      </button>
                      <button
                        type="button"
                        onClick={() => {
                          void (async () => {
                            const ok = await confirmAction(
                              "删除主题？",
                              `「${theme.name}」将移入回收站，雷达与来源不再展示。`,
                              "danger",
                            );
                            if (!ok) return;
                            try {
                              await deleteContentTheme(theme.id);
                              showNotice("主题已删除", "可在回收站恢复。");
                              await refreshCourseSystemThemes();
                            } catch (error) {
                              showNotice(
                                "删除失败",
                                error instanceof Error
                                  ? error.message
                                  : "请稍后重试",
                              );
                            }
                          })();
                        }}
                      >
                        删除
                      </button>
                    </>
                  )}
                </div>
              </div>
            ))
          )}
        </div>
      </div>
    </section>
  );
}
