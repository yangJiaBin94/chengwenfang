import { describe, expect, it } from "vitest";
import {
  isLegacyCourseDetailPath,
  resolveLegacyRedirect,
} from "./legacyRedirects";
import {
  NOTIFICATIONS_PAGE,
  normalizeSettingsSection,
  parseRoute,
} from "./routes";

describe("parseRoute U01–U20", () => {
  it("U01 / → dashboard", () => {
    expect(parseRoute("/")).toEqual({ page: "dashboard", params: {} });
  });

  it("U02 /modes/course-system → course-themes", () => {
    expect(parseRoute("/modes/course-system")).toEqual({
      page: "course-themes",
      params: {},
    });
  });

  it("U03 course theme wizard paths", () => {
    expect(parseRoute("/modes/course-system/themes/new")).toEqual({
      page: "course-theme-wizard",
      params: {},
    });
    expect(
      parseRoute("/modes/course-system/themes/ai-self-study-zero/wizard"),
    ).toEqual({
      page: "course-theme-wizard",
      params: { themeSlug: "ai-self-study-zero" },
    });
  });

  it("U04 course route", () => {
    expect(
      parseRoute("/modes/course-system/themes/ai-self-study-zero/route"),
    ).toEqual({
      page: "course-route",
      params: { themeSlug: "ai-self-study-zero" },
    });
  });

  it("U05 course detail", () => {
    expect(
      parseRoute(
        "/modes/course-system/themes/ai-self-study-zero/courses/course-rag-03e2336d-1",
      ),
    ).toEqual({
      page: "course-detail",
      params: {
        themeSlug: "ai-self-study-zero",
        courseId: "course-rag-03e2336d-1",
      },
    });
  });

  it("U06 workflows list and detail", () => {
    expect(parseRoute("/workflows")).toEqual({
      page: "workflows",
      params: {},
    });
    expect(parseRoute("/modes/course-system/workflows")).toEqual({
      page: "workflows",
      params: {},
    });
    expect(parseRoute("/workflows/wf-1")).toEqual({
      page: "workflows",
      params: { workflowId: "wf-1" },
    });
    expect(parseRoute("/modes/course-system/workflows/wf-2")).toEqual({
      page: "workflows",
      params: { workflowId: "wf-2" },
    });
  });

  it("U07 /modes/hot-news → hot-news-themes", () => {
    expect(parseRoute("/modes/hot-news")).toEqual({
      page: "hot-news-themes",
      params: {},
    });
  });

  it("U08 hot-news theme wizard paths", () => {
    expect(parseRoute("/modes/hot-news/themes/new")).toEqual({
      page: "hot-news-theme-wizard",
      params: {},
    });
    expect(parseRoute("/modes/hot-news/themes/ai-frontier/wizard")).toEqual({
      page: "hot-news-theme-wizard",
      params: { themeSlug: "ai-frontier" },
    });
  });

  it("U09 radar", () => {
    expect(parseRoute("/modes/hot-news/themes/ai-frontier/radar")).toEqual({
      page: "hot-news-radar",
      params: { themeSlug: "ai-frontier", view: "radar" },
    });
  });

  it("U10 selected", () => {
    expect(parseRoute("/modes/hot-news/themes/ai-frontier/selected")).toEqual({
      page: "hot-news-selected",
      params: { themeSlug: "ai-frontier", view: "selected" },
    });
  });

  it("U11 sources", () => {
    expect(parseRoute("/modes/hot-news/themes/ai-frontier/sources")).toEqual({
      page: "hot-news-sources",
      params: { themeSlug: "ai-frontier", view: "sources" },
    });
  });

  it("U12 collect-execution", () => {
    expect(
      parseRoute("/modes/hot-news/themes/ai-frontier/collect-execution"),
    ).toEqual({
      page: "hot-news-collect-execution",
      params: { themeSlug: "ai-frontier" },
    });
  });

  it("U13 research", () => {
    expect(
      parseRoute(
        "/modes/hot-news/topics/135a07fc-cc39-429a-9dd5-6455bba365b7/research",
      ),
    ).toEqual({
      page: "hot-news-research",
      params: { topicId: "135a07fc-cc39-429a-9dd5-6455bba365b7" },
    });
  });

  it("U14 article workspace", () => {
    expect(
      parseRoute(
        "/modes/hot-news/topics/topic-1/articles/article-2",
      ),
    ).toEqual({
      page: "hot-news-article",
      params: { topicId: "topic-1", articleId: "article-2" },
    });
  });

  it("U15 article execution", () => {
    expect(
      parseRoute("/modes/hot-news/articles/article-9/execution"),
    ).toEqual({
      page: "hot-news-execution",
      params: { articleId: "article-9" },
    });
  });

  it("U16 executions?job=", () => {
    expect(parseRoute("/executions", "?job=job-abc")).toEqual({
      page: "executions",
      params: { jobId: "job-abc" },
    });
    expect(parseRoute("/executions")).toEqual({
      page: "executions",
      params: { jobId: null },
    });
  });

  it("U17 settings sections", () => {
    expect(parseRoute("/settings", "?section=ai")).toEqual({
      page: "settings",
      params: { section: "ai" },
    });
    expect(parseRoute("/settings", "section=skills")).toEqual({
      page: "settings",
      params: { section: "skills" },
    });
    expect(parseRoute("/settings", "?section=layoutThemes")).toEqual({
      page: "settings",
      params: { section: "layoutThemes" },
    });
    expect(parseRoute("/settings", "?section=wechatMp")).toEqual({
      page: "settings",
      params: { section: "wechatMp" },
    });
    // 旧分区 publishing → layoutThemes
    expect(normalizeSettingsSection("publishing")).toBe("layoutThemes");
    expect(parseRoute("/settings", "?section=publishing")).toEqual({
      page: "settings",
      params: { section: "layoutThemes" },
    });
  });

  it("U18 /recycle-bin", () => {
    expect(parseRoute("/recycle-bin")).toEqual({
      page: "recycle-bin",
      params: {},
    });
  });

  it("U19 notifications are header-mounted (no URL path)", () => {
    expect(NOTIFICATIONS_PAGE).toBe("notifications");
    // 无独立路由；任意已知路径都不会解析为 notifications
    expect(parseRoute("/").page).not.toBe("notifications");
    expect(parseRoute("/settings").page).not.toBe("notifications");
  });

  it("U20 legacy redirects → target page + params", () => {
    // /courses/:courseId：不改写 URL，解析为课程详情
    const legacyCourse =
      "/courses/course-rag-03e2336d-1";
    expect(isLegacyCourseDetailPath(legacyCourse)).toBe(true);
    expect(parseRoute(legacyCourse)).toEqual({
      page: "course-detail",
      params: { courseId: "course-rag-03e2336d-1" },
    });

    const ctx = { hotNewsThemeCode: "ai_frontier" };

    const sourcesTarget = resolveLegacyRedirect(
      "/modes/hot-news/sources",
      ctx,
    );
    expect(sourcesTarget).toBe(
      "/modes/hot-news/themes/ai-frontier/sources",
    );
    expect(parseRoute(sourcesTarget!)).toEqual({
      page: "hot-news-sources",
      params: { themeSlug: "ai-frontier", view: "sources" },
    });

    const selectedTarget = resolveLegacyRedirect(
      "/modes/hot-news/selected",
      ctx,
    );
    expect(selectedTarget).toBe(
      "/modes/hot-news/themes/ai-frontier/selected",
    );
    expect(parseRoute(selectedTarget!)).toEqual({
      page: "hot-news-selected",
      params: { themeSlug: "ai-frontier", view: "selected" },
    });

    const radarTarget = resolveLegacyRedirect(
      "/modes/hot-news/topics/135a07fc-cc39-429a-9dd5-6455bba365b7",
      ctx,
    );
    expect(radarTarget).toBe("/modes/hot-news/themes/ai-frontier/radar");
    expect(parseRoute(radarTarget!)).toEqual({
      page: "hot-news-radar",
      params: { themeSlug: "ai-frontier", view: "radar" },
    });

    // 非旧路径不重写
    expect(resolveLegacyRedirect("/modes/hot-news/themes/ai-frontier/radar")).toBeNull();
    expect(resolveLegacyRedirect("/")).toBeNull();
  });
});
