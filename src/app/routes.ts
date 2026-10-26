/**
 * 路由表：路径模式 → 页面标识与参数解析。
 * 路径与查询语义与抽取前 App.tsx syncRoute 一致。
 */

/** 应用页面标识（对应 U01–U19；U20 为旧路径重写，见 legacyRedirects）。 */
export type AppPage =
  | "dashboard"
  | "course-themes"
  | "course-theme-wizard"
  | "course-route"
  | "course-detail"
  | "workflows"
  | "hot-news-themes"
  | "hot-news-theme-wizard"
  | "hot-news-radar"
  | "hot-news-selected"
  | "hot-news-sources"
  | "hot-news-collect-execution"
  | "hot-news-research"
  | "hot-news-article"
  | "hot-news-execution"
  | "executions"
  | "settings"
  | "recycle-bin"
  | "notifications";

/** 设置分区（含旧 key publishing → layoutThemes 归一化后的结果）。 */
export type SettingsSection = "ai" | "skills" | "layoutThemes" | "wechatMp";

/** 热点主题工作区子视图。 */
export type HotNewsWorkspaceView = "radar" | "selected" | "sources";

/** 路径解析出的参数（仅包含当前页相关字段）。 */
export type RouteParams = {
  /** 主题路径 slug（课程 / 热点工作区、向导恢复）。 */
  themeSlug?: string;
  /** 课程 ID（含旧路径 /courses/:courseId）。 */
  courseId?: string;
  /** 工作流 ID。 */
  workflowId?: string;
  /** 热点选题 ID。 */
  topicId?: string;
  /** 热点文章 / 作品 ID。 */
  articleId?: string;
  /** 热点工作区子视图。 */
  view?: HotNewsWorkspaceView;
  /** 设置分区。 */
  section?: SettingsSection;
  /** 执行轨迹 job 查询参数。 */
  jobId?: string | null;
};

/** 一次路径解析结果。 */
export type ParsedRoute = {
  page: AppPage;
  params: RouteParams;
};

/** 头部通知中心无独立 URL；仅作页面标识常量（U19）。 */
export const NOTIFICATIONS_PAGE: AppPage = "notifications";

/**
 * 解析 pathname + search 为页面与参数。
 * 调用方应先用 resolveLegacyRedirect 处理可重写的旧路径，再对本函数传入最终路径。
 *
 * @param pathname location.pathname
 * @param search location.search（可含或不含前导 ?）
 */
export function parseRoute(pathname: string, search = ""): ParsedRoute {
  const path = pathname || "/";
  const query = search.startsWith("?") ? search.slice(1) : search;
  const searchParams = new URLSearchParams(query);

  const themedWizardMatch = path.match(
    /^\/modes\/course-system\/themes\/(?:new|([^/]+)\/wizard)$/,
  );
  const themedCourseMatch = path.match(
    /^\/modes\/course-system\/themes\/([^/]+)\/courses\/(.+)$/,
  );
  const themedRouteMatch = path.match(
    /^\/modes\/course-system\/themes\/([^/]+)\/route$/,
  );
  const legacyCourseMatch = path.match(/^\/courses\/(.+)$/);
  const themedWorkflowMatch = path.match(
    /^\/modes\/course-system\/workflows\/(.+)$/,
  );
  const workflowMatch = path.match(/^\/workflows\/(.+)$/);
  const hotNewsThemeWizardMatch = path.match(
    /^\/modes\/hot-news\/themes\/(?:new|([^/]+)\/wizard)$/,
  );
  const hotNewsThemeWorkspaceMatch = path.match(
    /^\/modes\/hot-news\/themes\/([^/]+)\/(radar|selected|sources)$/,
  );
  const hotNewsExecutionMatch = path.match(
    /^\/modes\/hot-news\/articles\/([^/]+)\/execution$/,
  );
  const hotNewsCollectExecutionMatch = path.match(
    /^\/modes\/hot-news\/themes\/([^/]+)\/collect-execution$/,
  );
  const hotNewsArticleMatch = path.match(
    /^\/modes\/hot-news\/topics\/([^/]+)\/articles\/([^/]+)$/,
  );
  const hotNewsResearchMatch = path.match(
    /^\/modes\/hot-news\/topics\/([^/]+)\/research$/,
  );

  if (path === "/modes/course-system/themes/new" || themedWizardMatch) {
    return {
      page: "course-theme-wizard",
      params: {
        themeSlug:
          themedWizardMatch && themedWizardMatch[1]
            ? decodeURIComponent(themedWizardMatch[1])
            : undefined,
      },
    };
  }

  if (themedCourseMatch) {
    return {
      page: "course-detail",
      params: {
        themeSlug: decodeURIComponent(themedCourseMatch[1]),
        courseId: decodeURIComponent(themedCourseMatch[2]),
      },
    };
  }

  if (themedRouteMatch) {
    return {
      page: "course-route",
      params: {
        themeSlug: decodeURIComponent(themedRouteMatch[1]),
      },
    };
  }

  if (path === "/courses") {
    return { page: "course-route", params: {} };
  }

  if (legacyCourseMatch) {
    return {
      page: "course-detail",
      params: {
        courseId: decodeURIComponent(legacyCourseMatch[1]),
      },
    };
  }

  if (themedWorkflowMatch || workflowMatch) {
    return {
      page: "workflows",
      params: {
        workflowId: decodeURIComponent(
          (themedWorkflowMatch || workflowMatch)![1],
        ),
      },
    };
  }

  if (path === "/modes/hot-news/themes/new" || hotNewsThemeWizardMatch) {
    return {
      page: "hot-news-theme-wizard",
      params: {
        themeSlug:
          hotNewsThemeWizardMatch && hotNewsThemeWizardMatch[1]
            ? decodeURIComponent(hotNewsThemeWizardMatch[1])
            : undefined,
      },
    };
  }

  if (hotNewsThemeWorkspaceMatch) {
    const view = hotNewsThemeWorkspaceMatch[2] as HotNewsWorkspaceView;
    const page: AppPage =
      view === "radar"
        ? "hot-news-radar"
        : view === "selected"
          ? "hot-news-selected"
          : "hot-news-sources";
    return {
      page,
      params: {
        themeSlug: decodeURIComponent(hotNewsThemeWorkspaceMatch[1]),
        view,
      },
    };
  }

  if (hotNewsExecutionMatch) {
    return {
      page: "hot-news-execution",
      params: {
        articleId: decodeURIComponent(hotNewsExecutionMatch[1]),
      },
    };
  }

  if (hotNewsCollectExecutionMatch) {
    return {
      page: "hot-news-collect-execution",
      params: {
        themeSlug: decodeURIComponent(hotNewsCollectExecutionMatch[1]),
      },
    };
  }

  if (hotNewsArticleMatch) {
    return {
      page: "hot-news-article",
      params: {
        topicId: decodeURIComponent(hotNewsArticleMatch[1]),
        articleId: decodeURIComponent(hotNewsArticleMatch[2]),
      },
    };
  }

  if (hotNewsResearchMatch) {
    return {
      page: "hot-news-research",
      params: {
        topicId: decodeURIComponent(hotNewsResearchMatch[1]),
      },
    };
  }

  if (path === "/modes/hot-news") {
    return { page: "hot-news-themes", params: {} };
  }

  if (path === "/workflows" || path === "/modes/course-system/workflows") {
    return { page: "workflows", params: {} };
  }

  if (path === "/recycle-bin") {
    return { page: "recycle-bin", params: {} };
  }

  if (path === "/modes/course-system") {
    return { page: "course-themes", params: {} };
  }

  if (path === "/settings") {
    const sectionRaw = searchParams.get("section");
    const section = normalizeSettingsSection(sectionRaw);
    return {
      page: "settings",
      params: section ? { section } : {},
    };
  }

  if (path === "/executions") {
    return {
      page: "executions",
      params: {
        jobId: searchParams.get("job"),
      },
    };
  }

  return { page: "dashboard", params: {} };
}

/**
 * 设置分区查询参数归一化：publishing 为旧分区 key，兼容为 layoutThemes。
 *
 * @param sectionRaw 原始 section 查询值
 * @return 合法分区或 undefined
 */
export function normalizeSettingsSection(
  sectionRaw: string | null | undefined,
): SettingsSection | undefined {
  if (sectionRaw === "publishing") {
    return "layoutThemes";
  }
  if (
    sectionRaw === "ai" ||
    sectionRaw === "skills" ||
    sectionRaw === "layoutThemes" ||
    sectionRaw === "wechatMp"
  ) {
    return sectionRaw;
  }
  return undefined;
}
