import type { Dispatch, SetStateAction } from "react";
import {
  Activity,
  ChevronDown,
  ChevronRight,
  FileText,
  GitBranch,
  Layers3,
  LayoutDashboard,
  LibraryBig,
  Search,
  Settings,
  Trash2,
} from "lucide-react";
import { hotNewsThemePath } from "../features/course/api";
import type { ContentThemeData } from "../features/course/api";
import { NavButton } from "./appShellUi";

export type AppSidebarProps = {
  activeNav: string;
  courseModeExpanded: boolean;
  setCourseModeExpanded: Dispatch<SetStateAction<boolean>>;
  hotNewsModeExpanded: boolean;
  setHotNewsModeExpanded: Dispatch<SetStateAction<boolean>>;
  navigate: (path: string) => void | Promise<void>;
  themeRoutePath: string;
  routeHotNewsRadar: boolean;
  routeHotNewsSelected: boolean;
  routeHotNewsSourceManagement: boolean;
  routeHotNewsTopicId: string | null;
  routeHotNewsArticleId: string | null;
  routeHotNewsResearchTopicId: string | null;
  routeHotNewsExecutionArticleId: string | null;
  routeHotNewsCollectThemeId: string | null;
  hotNewsThemeWizardOpen: boolean;
  setHotNewsThemePickerOpen: (open: boolean) => void;
  selectedHotNewsThemeMeta: ContentThemeData | null;
};

export function AppSidebar({
  activeNav,
  courseModeExpanded,
  setCourseModeExpanded,
  hotNewsModeExpanded,
  setHotNewsModeExpanded,
  navigate,
  themeRoutePath,
  routeHotNewsRadar,
  routeHotNewsSelected,
  routeHotNewsSourceManagement,
  routeHotNewsTopicId,
  routeHotNewsArticleId,
  routeHotNewsResearchTopicId,
  routeHotNewsExecutionArticleId,
  routeHotNewsCollectThemeId,
  hotNewsThemeWizardOpen,
  setHotNewsThemePickerOpen,
  selectedHotNewsThemeMeta,
}: AppSidebarProps) {
  return (
        <aside className="sidebar">
          <div className="brand-block">
            <div className="brand-mark">
              <Layers3 size={22} />
            </div>
            <div>
              <div className="brand-title">成文坊</div>
              <div className="brand-subtitle">本地内容生产系统</div>
            </div>
          </div>

          <nav className="nav-stack">
            <NavButton
              active={activeNav === "dashboard"}
              icon={LayoutDashboard}
              label="首页"
              onClick={() => navigate("/")}
            />
            <button
              className={
                activeNav === "mode"
                  ? "nav-button active"
                  : activeNav === "learning"
                    ? "nav-button section-active"
                    : "nav-button"
              }
              type="button"
              aria-expanded={courseModeExpanded}
              onClick={() => {
                setCourseModeExpanded((expanded) => !expanded);
                navigate("/modes/course-system");
              }}
            >
              <Layers3 size={18} />
              <span>课程体系教学模式</span>
              {courseModeExpanded ? (
                <ChevronDown size={15} />
              ) : (
                <ChevronRight size={15} />
              )}
            </button>
            {courseModeExpanded && (
              <div className="nav-submenu">
                <NavButton
                  active={activeNav === "learning"}
                  icon={LibraryBig}
                  label="课程路线"
                  onClick={() => navigate(themeRoutePath)}
                />
              </div>
            )}
            <button
              className={
                activeNav === "hot-news" &&
                !routeHotNewsRadar &&
                !routeHotNewsSelected &&
                !routeHotNewsSourceManagement &&
                !routeHotNewsTopicId &&
                !routeHotNewsArticleId &&
                !routeHotNewsResearchTopicId &&
                !routeHotNewsExecutionArticleId &&
                !routeHotNewsCollectThemeId &&
                !hotNewsThemeWizardOpen
                  ? "nav-button active"
                  : activeNav === "hot-news"
                    ? "nav-button section-active"
                    : "nav-button"
              }
              type="button"
              aria-expanded={hotNewsModeExpanded}
              onClick={() => {
                setHotNewsThemePickerOpen(false);
                setHotNewsModeExpanded((expanded) => !expanded);
                void navigate("/modes/hot-news");
              }}
            >
              <Activity size={18} />
              <span>热点资讯模式</span>
              {hotNewsModeExpanded ? (
                <ChevronDown size={15} />
              ) : (
                <ChevronRight size={15} />
              )}
            </button>
            {hotNewsModeExpanded && (
              <div className="nav-submenu">
                <NavButton
                  active={activeNav === "hot-news" && routeHotNewsRadar}
                  icon={Search}
                  label="热点雷达"
                  onClick={() => {
                    setHotNewsThemePickerOpen(false);
                    void navigate(
                      hotNewsThemePath(
                        selectedHotNewsThemeMeta?.code || "ai_frontier",
                        "radar",
                      ),
                    );
                  }}
                />
                <NavButton
                  active={activeNav === "hot-news" && routeHotNewsSelected}
                  icon={FileText}
                  label="已选题"
                  onClick={() => {
                    setHotNewsThemePickerOpen(false);
                    void navigate(
                      hotNewsThemePath(
                        selectedHotNewsThemeMeta?.code || "ai_frontier",
                        "selected",
                      ),
                    );
                  }}
                />
                <NavButton
                  active={
                    activeNav === "hot-news" && routeHotNewsSourceManagement
                  }
                  icon={Settings}
                  label="来源管理"
                  onClick={() => {
                    setHotNewsThemePickerOpen(false);
                    void navigate(
                      hotNewsThemePath(
                        selectedHotNewsThemeMeta?.code || "ai_frontier",
                        "sources",
                      ),
                    );
                  }}
                />
              </div>
            )}
            <NavButton
              active={activeNav === "workflows"}
              icon={GitBranch}
              label="工作流"
              onClick={() => navigate("/modes/course-system/workflows")}
            />
            <NavButton
              active={activeNav === "recycle-bin"}
              icon={Trash2}
              label="回收站"
              onClick={() => navigate("/recycle-bin")}
            />
            <NavButton
              active={activeNav === "settings"}
              icon={Settings}
              label="设置"
              onClick={() => navigate("/settings")}
            />
          </nav>
        </aside>

  );
}
