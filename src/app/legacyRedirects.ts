/**
 * 旧路径重写规则（与抽取前 App.tsx syncRoute 行为一致）。
 * /courses/:courseId 仍可解析为课程详情，但不改写 URL。
 */

import { themePathSlug } from "../shared/theme/themeSlug";

/** 热点工作区旧路径对应的子视图。 */
export type LegacyHotNewsView = "radar" | "selected" | "sources";

/** 解析旧路径时需要的上下文（主题 code 回退）。 */
export type LegacyRedirectContext = {
  /** 当前选中或首个启用热点主题的 code；缺省时用 ai_frontier。 */
  hotNewsThemeCode?: string;
};

/**
 * 若 pathname 为可重写的旧路径，返回目标完整路径；否则返回 null。
 *
 * 覆盖：
 * - /modes/hot-news/sources → /modes/hot-news/themes/:slug/sources
 * - /modes/hot-news/selected → .../selected
 * - /modes/hot-news/topics/:topicId（无 research/articles 后缀）→ .../radar
 *
 * @param pathname location.pathname
 * @param context 主题 code 回退
 */
export function resolveLegacyRedirect(
  pathname: string,
  context: LegacyRedirectContext = {},
): string | null {
  const view = matchLegacyHotNewsWorkspacePath(pathname);
  if (!view) {
    return null;
  }
  const code = context.hotNewsThemeCode || "ai_frontier";
  return buildHotNewsWorkspacePath(code, view);
}

/**
 * 识别热点旧工作区路径并返回目标子视图。
 *
 * @param pathname location.pathname
 * @return 子视图或 null
 */
export function matchLegacyHotNewsWorkspacePath(
  pathname: string,
): LegacyHotNewsView | null {
  if (pathname === "/modes/hot-news/sources") {
    return "sources";
  }
  if (pathname === "/modes/hot-news/selected") {
    return "selected";
  }
  const legacyRadarMatch = pathname.match(/^\/modes\/hot-news\/topics\/([^/]+)$/);
  if (legacyRadarMatch) {
    return "radar";
  }
  return null;
}

/**
 * 构造热点主题工作区路径（与 content-client.hotNewsThemePath 同形）。
 *
 * @param themeCode 主题 code
 * @param view 工作区子视图
 */
export function buildHotNewsWorkspacePath(
  themeCode: string,
  view: LegacyHotNewsView | "wizard",
): string {
  return `/modes/hot-news/themes/${themePathSlug(themeCode)}/${view}`;
}

/**
 * 是否为旧课程详情路径（/courses/:courseId）；URL 保持不变，仅语义上视为课程详情。
 *
 * @param pathname location.pathname
 */
export function isLegacyCourseDetailPath(pathname: string): boolean {
  return (
    /^\/courses\/(.+)$/.test(pathname) &&
    pathname !== "/courses" &&
    !pathname.startsWith("/modes/")
  );
}
