import { describe, expect, it } from "vitest";
import {
  findThemeBySlug,
  resolveCourseThemePathKey,
  resolveHotNewsThemeSlug,
  themeCodeFromSlug,
  themeMatchesSlug,
  themePathSlug,
} from "./themeSlug";

describe("themeSlug", () => {
  it("converts underscore and hyphen", () => {
    expect(themePathSlug("ai_frontier")).toBe("ai-frontier");
    expect(themeCodeFromSlug("ai-frontier")).toBe("ai_frontier");
  });

  it("matches slug / code / id like App.tsx used to", () => {
    const theme = { id: "tid-1", code: "ai_frontier" };
    expect(themeMatchesSlug(theme, "ai-frontier")).toBe(true);
    expect(themeMatchesSlug(theme, "ai_frontier")).toBe(true);
    expect(themeMatchesSlug(theme, "tid-1")).toBe(true);
    expect(themeMatchesSlug(theme, "other")).toBe(false);
    expect(findThemeBySlug([theme], "ai-frontier")).toEqual(theme);
  });

  it("resolves notification metadata path keys", () => {
    expect(resolveHotNewsThemeSlug({ themeCode: "ai_frontier" })).toBe(
      "ai-frontier",
    );
    expect(resolveHotNewsThemeSlug({ themeId: "tid" })).toBe("tid");
    expect(resolveCourseThemePathKey({ themeCode: "self_study" })).toBe(
      "self-study",
    );
    expect(resolveCourseThemePathKey({})).toBe("ai-self-study-zero");
  });
});
