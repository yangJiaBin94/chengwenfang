import {
  Archive,
  BookOpen,
  ClipboardCheck,
  FileText,
  Image,
  MonitorCheck,
} from "lucide-react";
/** Course production states displayed throughout the workbench. */
export type CourseStatus =
  | "素材已准备"
  | "原稿已生成"
  | "公众号稿已生成"
  | "图片已生成"
  | "已排版"
  | "AI 已检查"
  | "人工已退回"
  | "人工已审核";

/** Frontend course record returned by the content index API. */
export type Course = {
  id: string;
  topicSlug?: string;
  series: string;
  lesson: string;
  lessonNo?: number;
  title: string;
  status: CourseStatus;
  progress: number;
  draftWords: number;
  articleWords: number;
  images: number;
  reviewScore: number;
  updatedAt: string;
  summary: string;
  draftPath?: string;
  wechatPath?: string;
  draftPublicPath?: string;
  wechatPublicPath?: string;
  imagePaths?: string[];
  imagePublicPaths?: string[];
  imageAssets?: ImageAsset[];
  contentHash?: string;
  draftExcerpt?: string;
  articleExcerpt?: string;
};

/** 课程列表为空时的占位，避免 selectedCourse 为 undefined 导致 hook 依赖崩溃。 */
export const emptyCourse: Course = {
  id: "",
  series: "",
  lesson: "",
  title: "",
  status: "素材已准备",
  progress: 0,
  draftWords: 0,
  articleWords: 0,
  images: 0,
  reviewScore: 0,
  updatedAt: "",
  summary: "",
};

/** Current draft and article bodies loaded for the selected course. */
export type CourseContent = {
  draft: string;
  article: string;
  draftArticleId: string | null;
  articleArticleId: string | null;
  /** 原稿统一文档 id；缺省时与 draftArticleId 同值。 */
  draftDocumentId: string | null;
  /** 公众号统一文档 id；缺省时与 articleArticleId 同值。 */
  articleDocumentId: string | null;
  images: ImageAsset[];
  loading: boolean;
};

/** Prefer explicit documentId from API; course track falls back to articleId (1:1). */
export function preferCourseDocumentId(
  documentId: string | null | undefined,
  articleId: string | null | undefined,
): string | null {
  return documentId || articleId || null;
}

/** Normalized image reference collected from content or structured assets. */
export type SyncedImage = {
  src: string;
  alt: string;
  source: "课程原稿" | "公众号文章" | "配套图片";
};

/** Structured image metadata imported with a course. */
export type ImageAsset = {
  name: string;
  path: string;
  articleId: string;
  articleTitle: string;
  articlePath: string;
};

/** Complete dashboard payload returned by the backend. */
export type WorkbenchData = {
  workspace: {
    name: string;
    moduleName: string;
    topicName: string;
    topicSlug: string;
    modeId: string;
    modeName: string;
    modeCode: string;
    themeId: string;
    themeName: string;
    themeCode: string;
  };
  generatedAt: string;
  courses: Course[];
  metrics: {
    courseCount: number;
    reviewedCount: number;
    pendingCount: number;
    imageCount: number;
  };
};

/** Detail-panel tabs supported by the current UI. */
export type TabKey = "draft" | "article" | "images" | "mdnice" | "review" | "versions";

export {
  wordCountLabels,
  imageCountLabels,
} from "../../shared/format/wordImageLabels";

/** Visual audit statuses shared with the Python audit response. */
export type AuditStatus = "pass" | "warn" | "fail";

/** One audit rule result displayed in the review list. */
export type AuditItem = {
  code: string;
  source: string;
  severity: string;
  title: string;
  detail: string;
  status: AuditStatus;
  fixable: boolean;
  fixableByTextAi: boolean;
  blocking: boolean;
  location?: string | null;
  suggestion?: string | null;
};

/** Localized copy for persisted audits created before backend localization. */
export const auditRuleCopy: Record<string, { title: string; detail: string }> = {
  TITLE_NO_COURSE_NUMBER: {
    title: "标题不包含课程编号",
    detail: "公众号标题不能包含“第几课”等课程编号。",
  },
  NO_HORIZONTAL_RULE: {
    title: "不使用水平分隔线",
    detail: "公众号正文不能使用 Markdown 水平分隔线。",
  },
  NO_MARKDOWN_TABLE: {
    title: "表格转换为列表",
    detail: "公众号正文中的 Markdown 表格需要转换为可读列表。",
  },
  NO_ENGLISH_QUOTES: {
    title: "使用中文双引号",
    detail: "中文对话和引用应使用中文双引号。",
  },
  NO_CODE_FENCE: {
    title: "不保留代码围栏",
    detail: "公众号文章不能保留三个反引号组成的代码围栏。",
  },
  NO_AI_CLICHES: {
    title: "避免 AI 套话",
    detail: "正文应避免知识库规范中列出的 AI 套话和模板化词汇。",
  },
  FORMULA_BLOCK_STYLE: {
    title: "独立公式使用引用块",
    detail: "独立数学公式应使用“> 公式”的 Markdown 引用块样式。",
  },
  FORMULA_PLAIN_EXPRESSION: {
    title: "公式使用普通可读表达式",
    detail: "公式中不应显示 LaTeX 排版命令，应使用函数名、括号和斜杠表达。",
  },
  CONTENT_PRESERVATION: {
    title: "公众号内容完整保留",
    detail: "公众号版本需保留课程原稿中的核心知识、公式、案例和推理。",
  },
};

/** Return Chinese audit copy while preserving unknown custom rule text. */
export function localizedAuditItem(item: AuditItem) {
  return auditRuleCopy[item.code] ?? { title: item.title, detail: item.detail };
}

/** Complete audit report used by review and repair summaries. */
export type AuditReport = {
  id: string;
  articleId: string;
  articleVersionId: string | null;
  type: string;
  score: number;
  deterministicScore: number;
  semanticScore: number | null;
  passed: number;
  warnings: number;
  failed: number;
  summary: string;
  createdAt: string;
  items: AuditItem[];
};

export const emptyAuditReport: AuditReport = {
  id: "",
  articleId: "",
  articleVersionId: null,
  type: "deterministic",
  score: 0,
  deterministicScore: 0,
  semanticScore: null,
  passed: 0,
  warnings: 0,
  failed: 0,
  summary: "",
  createdAt: "",
  items: [],
};

/** Human review decision returned by the version-bound database API. */
export type HumanReviewStatus = "pending" | "approved" | "rejected";

/** Current human decision, comment, and submission time. */
export type HumanReview = {
  documentId?: string | null;
  documentVersionId?: string | null;
  /** @deprecated 请用 documentVersionId */
  articleVersionId?: string | null;
  status: HumanReviewStatus;
  comment: string;
  reviewedAt: string;
};

/** Article version row rendered by the version-history tab. */
export type ArticleVersion = {
  id: string;
  articleId: string;
  versionNo: number;
  createdAt: string;
  source: string;
  reason: string;
  content: string;
  workflowRunId?: string | null;
  audit?: Pick<AuditReport, "score" | "passed" | "warnings" | "failed">;
};

export type UnifiedContentVersion = ArticleVersion & {
  draftVersion?: ArticleVersion;
  articleVersion?: ArticleVersion;
};

export const statusTone: Record<string, string> = {
  素材已准备: "tone-muted",
  原稿已生成: "tone-blue",
  公众号稿已生成: "tone-amber",
  图片已生成: "tone-lime",
  已排版: "tone-cyan",
  "AI 已检查": "tone-indigo",
  人工已退回: "tone-red",
  人工已审核: "tone-green",
  draft: "tone-muted",
  generated: "tone-blue",
  published: "tone-green",
};

/** 课程状态展示文案（兼容英文壳状态）。 */
export function formatCourseStatus(status: string | undefined | null): string {
  const raw = (status || "").trim();
  if (!raw) return "素材已准备";
  const labels: Record<string, string> = {
    draft: "素材已准备",
    generated: "已生成",
    published: "已发布",
  };
  return labels[raw] || raw;
}

export const tabs: Array<{ key: TabKey; label: string; icon: typeof FileText }> = [
  { key: "draft", label: "课程原稿", icon: FileText },
  { key: "article", label: "公众号文章", icon: BookOpen },
  { key: "images", label: "配套图片", icon: Image },
  { key: "mdnice", label: "公众号排版", icon: MonitorCheck },
  { key: "review", label: "审核", icon: ClipboardCheck },
  { key: "versions", label: "版本", icon: Archive },
];
export const nativePublishingEnabled =
  import.meta.env.VITE_NATIVE_PUBLISHING_ENABLED !== "false";

/** Convert imported relative image paths into API file URLs when possible. */
export function resolveImageSrc(src: string) {
  const normalizedSrc = src.replace(/^\.?\.\//, "").replace(/^\.\//, "");
  if (
    normalizedSrc.startsWith("/api/") ||
    normalizedSrc.startsWith("http://") ||
    normalizedSrc.startsWith("https://")
  ) {
    return normalizedSrc;
  }
  // Legacy static paths remain until sync rewrites markdown to /api/files/{id}.
  if (normalizedSrc.startsWith("/content/") || normalizedSrc.startsWith("content/")) {
    return normalizedSrc.startsWith("/") ? normalizedSrc : `/${normalizedSrc}`;
  }
  return normalizedSrc.startsWith("images/")
    ? `/content/learning-content/topics/ai-basics/${normalizedSrc}`
    : normalizedSrc;
}

/** Extract a readable file name from an image URL or local path. */
export function imageNameFromSrc(src: string) {
  const cleanSrc = src.split(/[?#]/)[0];
  const fileName = cleanSrc.split(/[\\/]/).filter(Boolean).pop() ?? cleanSrc;

  try {
    return decodeURIComponent(fileName);
  } catch {
    return fileName;
  }
}

/** Convert an arbitrary asset file name into a stable public-path segment. */
export function safeAssetName(fileName: string) {
  return fileName
    .replace(/[^\p{L}\p{N}._-]+/gu, "-")
    .replace(/-+/g, "-")
    .replace(/^-|-$/g, "");
}

/** Convert Obsidian image embeds into Markdown; prefer API file URLs from media map. */
export function normalizeObsidianEmbeds(
  content: string,
  mediaByName?: Map<string, string>,
) {
  return content.replace(
    /!\[\[([^\]|]+)(?:\|[^\]]+)?\]\]/g,
    (_, fileName: string) => {
      const cleanName = fileName.trim();
      const apiUrl = mediaByName?.get(cleanName) ?? mediaByName?.get(safeAssetName(cleanName));
      if (apiUrl) {
        return `![${cleanName}](${apiUrl})`;
      }
      return `![${cleanName}](/content/learning-content/topics/ai-basics/assets/${safeAssetName(cleanName)})`;
    },
  );
}

/** Collect structured asset references from Markdown content. */
export function extractMarkdownImages(content: string, source: SyncedImage["source"]) {
  const normalizedContent = normalizeObsidianEmbeds(content);
  const matches = [...normalizedContent.matchAll(/!\[([^\]]*)\]\(([^)]+)\)/g)];
  return matches
    .map((match) => ({
      alt: match[1] || "未命名插图",
      src: resolveImageSrc(match[2]),
      source,
    }))
    .filter((image) => image.src.includes("/assets/") || image.src.includes("/api/files/") || image.src.includes("/api/images/"));
}

export type IllustrationPrompt = { prompt: string; marker: string; index: number };

/** Extract knowledge-base style prompt blocks and their full replacement markers. */
export function extractIllustrationPrompts(content: string): IllustrationPrompt[] {
  const lines = content.split("\n");
  const prompts: IllustrationPrompt[] = [];
  for (let index = 0; index < lines.length; index += 1) {
    if (!/提示词|prompt/i.test(lines[index])) continue;
    let fence = index + 1;
    while (fence < lines.length && !/```/.test(lines[fence])) fence += 1;
    if (fence >= lines.length) continue;
    const opening = fence;
    fence = opening + 1;
    while (fence < lines.length && !/```/.test(lines[fence])) fence += 1;
    if (fence >= lines.length) continue;
    const prompt = lines
      .slice(opening + 1, fence)
      .map((line) => line.replace(/^\s*>\s?/, ""))
      .join("\n")
      .trim();
    if (!prompt) continue;
    const marker = lines.slice(index, fence + 1).join("\n");
    prompts.push({ prompt, marker, index });
    index = fence;
  }
  return prompts;
}

/** Remove image prompt headings and normalize Markdown for mdnice. */
export function prepareMdniceMarkdown(content: string) {
  return normalizeObsidianEmbeds(content)
    .replace(
      /^#{1,6}\s*(?:封面图|插图\s*\d+|图片\s*\d*)\s*[:：].*\n+(?=\s*!\[)/gm,
      "",
    )
    .replace(/[ \t]+\n/g, "\n")
    .replace(/\n{3,}/g, "\n\n")
    .trim();
}

/** Deduplicate image references by their resolved source path. */
export function uniqueImages(images: SyncedImage[]) {
  const seen = new Set<string>();
  return images.filter((image) => {
    if (seen.has(image.src)) return false;
    seen.add(image.src);
    return true;
  });
}
