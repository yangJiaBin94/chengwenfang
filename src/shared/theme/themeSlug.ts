/** 主题路径 slug：code 中下划线改为连字符。 */
export function themePathSlug(code: string) {
  return String(code || "").replaceAll("_", "-");
}

/** 从路径 slug 还原主题 code。 */
export function themeCodeFromSlug(slug: string) {
  return String(slug || "").replaceAll("-", "_");
}

/** 主题是否匹配路径中的 slug（slug / code / id 任一命中）。 */
export function themeMatchesSlug(
  theme: { id: string; code: string },
  slug: string,
): boolean {
  return (
    themePathSlug(theme.code) === slug ||
    theme.code === themeCodeFromSlug(slug) ||
    theme.id === slug
  );
}

/**
 * 在主题列表中按路径 slug 查找；找不到返回 undefined。
 */
export function findThemeBySlug<T extends { id: string; code: string }>(
  themes: T[],
  slug: string,
): T | undefined {
  return themes.find((theme) => themeMatchesSlug(theme, slug));
}

/**
 * 从通知 metadata 解析热点主题路径段（优先 themeCode，其次 themeId）。
 */
export function resolveHotNewsThemeSlug(
  metadata: Record<string, unknown> | undefined,
): string {
  const code =
    metadata?.themeCode != null ? String(metadata.themeCode).trim() : "";
  if (code) {
    return themePathSlug(code);
  }
  const themeId =
    metadata?.themeId != null ? String(metadata.themeId).trim() : "";
  return themeId;
}

/**
 * 从通知 metadata 解析课程主题路径段（优先 code，其次 themeId，缺省回退种子主题）。
 */
export function resolveCourseThemePathKey(
  metadata: Record<string, unknown> | undefined,
): string {
  const code =
    metadata?.themeCode != null ? String(metadata.themeCode).trim() : "";
  if (code) {
    return code.replaceAll("_", "-");
  }
  const themeId =
    metadata?.themeId != null ? String(metadata.themeId).trim() : "";
  if (themeId) {
    return themeId;
  }
  return "ai-self-study-zero";
}
