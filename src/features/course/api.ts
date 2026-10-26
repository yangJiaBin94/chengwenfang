import { unwrapApiResult } from "../../shared/api/http";
import {
  loadJob,
  loadJobArticleVersions,
  loadJobEventHistory,
  loadJobNodes,
  retryFailedJob,
  waitForBackgroundJob,
  type JobData,
  type JobEventData,
  type WorkflowNodeData,
} from "../../shared/api/jobs";
import { themeCodeFromSlug, themePathSlug } from "../../shared/theme/themeSlug";

export type { JobData, JobEventData, WorkflowNodeData };
export {
  loadJob,
  loadJobArticleVersions,
  loadJobEventHistory,
  loadJobNodes,
  retryFailedJob,
  waitForBackgroundJob,
};
export { themeCodeFromSlug, themePathSlug };

/** Current draft and article bodies for one course. */
export type CourseMediaAsset = {
  id: string;
  fileId?: string;
  name: string;
  path: string;
  url: string;
  publicPath?: string | null;
  sequenceNo?: number;
};

export type CourseContentData = {
  courseId: string;
  draftArticleId: string | null;
  articleArticleId: string | null;
  draftDocumentId?: string | null;
  articleDocumentId?: string | null;
  draftFileId?: string | null;
  articleFileId?: string | null;
  draft: string;
  article: string;
  images?: CourseMediaAsset[];
};

export type WordCountOption = "under-2000" | "2000-4000" | "4000-6000" | "6000-8000";
export type ImageCountOption = "up-to-3" | "3-5" | "5-7";
export type CourseGenerationSettingsData = {
  courseId: string;
  wordCountOption: WordCountOption;
  imageCountOption: ImageCountOption;
  recommendedWordCountOption: WordCountOption;
  recommendedImageCountOption: ImageCountOption;
  recommendationReason: string;
  recommendedAt: string;
  fromCache: boolean;
};

export type ContentThemeData = {
  id: string;
  modeId: string;
  code: string;
  name: string;
  description: string;
  status: string;
  seriesCount: number;
  courseCount: number;
  completedCount: number;
  itemCount?: number;
  candidateCount?: number;
  createdVia?: string;
  briefingJson?: string | null;
  curriculumDraftJson?: string | null;
  routeNarrative?: string | null;
  defaultsJson?: string | null;
  generateJobId?: string | null;
  deletedAt?: string | null;
};

export type CurriculumLessonDraft = {
  lessonNo: number;
  title: string;
  summary: string;
  learningOutcomes?: string[];
};

export type CurriculumSeriesDraft = {
  tempId?: string;
  name: string;
  sortOrder: number;
  positioning?: string;
  lessons: CurriculumLessonDraft[];
};

export type CurriculumDraftData = {
  schemaVersion: number;
  routeNarrative: string;
  series: CurriculumSeriesDraft[];
};

export type ThemeCurriculumGenerateData = {
  themeId: string;
  jobId: string;
  status: string;
};

export type ThemeCurriculumCommitData = {
  themeId: string;
  status: string;
  seriesCount: number;
  courseCount: number;
};

export type ContentModeData = {
  id: string;
  code: string;
  name: string;
  description: string;
  status: string;
  themes: ContentThemeData[];
};

/** Current mutable article state returned by content writes and repairs. */
export type ArticleData = {
  id: string;
  /** 统一文档 ID（课程侧与 articles.id 相同） */
  documentId?: string | null;
  courseId: string;
  kind: "course_draft" | "wechat_article";
  content: string;
  contentHash: string;
  wordCount: number;
  currentVersionNo: number;
  updatedAt: string;
};

/** Immutable article snapshot shown in the version history tab. */
export type ArticleVersionData = {
  id: string;
  articleId: string;
  versionNo: number;
  content: string;
  contentHash: string;
  source: string;
  reason: string;
  createdAt: string;
  workflowRunId?: string | null;
  audit?: { score: number; passed: number; warnings: number; failed: number };
};

/** Human decision bound to the exact article version reviewed by a person. */
export type HumanReviewData = {
  id: string;
  articleId: string;
  /** 统一文档 ID */
  documentId?: string | null;
  /** 文档版本 ID（content_document_versions.id） */
  documentVersionId?: string | null;
  /** @deprecated 请用 documentVersionId */
  articleVersionId?: string | null;
  versionNo: number;
  status: "approved" | "rejected";
  comment: string;
  reviewedAt: string;
};

/** One stable audit rule result returned by the Python engine. */
export type AuditItemData = {
  code: string;
  source: string;
  severity: string;
  title: string;
  detail: string;
  status: "pass" | "warn" | "fail";
  fixable: boolean;
  fixableByTextAi: boolean;
  blocking: boolean;
  location?: string | null;
  suggestion?: string | null;
};

/** Complete persisted audit report. */
export type AuditReportData = {
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
  items: AuditItemData[];
};

/** Atomic AI repair result including versions, audits, and issue changes. */
export type RepairResponseData = {
  repairRunId: string;
  status: string;
  article: ArticleData;
  beforeVersionId: string;
  afterVersionId: string;
  beforeAudit: AuditReportData;
  afterAudit: AuditReportData;
  resolvedCodes: string[];
  addedCodes: string[];
  createdAt: string;
  finishedAt: string;
};

/** Native publishing theme exposed by the chengwenfang. */
export type LayoutThemeData = {
  id: string;
  key: string;
  name: string;
  description: string;
  type: "system" | "custom";
  status: "enabled" | "disabled";
  currentVersionId: string;
  versionNo: number;
  config: Record<string, unknown>;
  updatedAt: string;
};

/** Deterministic render output shared by preview and clipboard export. */
export type PublishingRenderData = {
  html: string;
  plainText: string;
  markdownHash: string;
  themeVersionId: string;
  images: Array<Record<string, unknown>>;
  diagnostics: Array<Record<string, unknown>>;
};

/** Explicitly persisted article layout settings. */
export type ArticleLayoutData = {
  id: string;
  articleId: string;
  articleVersionNo: number;
  themeVersionId: string;
  options: Record<string, unknown>;
  updatedAt: string;
};

/** Immutable HTML publication snapshot. */
export type PublishingSnapshotData = {
  id: string;
  articleId: string;
  articleVersionNo: number;
  themeVersionId: string;
  html: string;
  htmlHash: string;
  markdownHash: string;
  imageCount: number;
  diagnostics: Array<Record<string, unknown>>;
  createdAt: string;
};

/** Parse a JSON response and preserve request IDs in user-facing errors. */
async function readPayload<T>(response: Response) {
  return unwrapApiResult<T>(response);
}

/** Load the built-in and user-created native publishing themes. */
export async function listLayoutThemes() {
  return readPayload<LayoutThemeData[]>(await fetch("/api/layout/themes", { cache: "no-store" }));
}

/** Create a user-owned theme from a validated configuration. */
export async function createLayoutTheme(body: { name: string; description: string; config: Record<string, unknown> }) {
  return readPayload<LayoutThemeData>(await fetch("/api/layout/themes", { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify(body) }));
}

/** Update a user-owned theme; the server creates an immutable theme version. */
export async function updateLayoutTheme(id: string, body: { name: string; description: string; config: Record<string, unknown> }) {
  return readPayload<LayoutThemeData>(await fetch(`/api/layout/themes/${encodeURIComponent(id)}`, { method: "PUT", headers: { "Content-Type": "application/json" }, body: JSON.stringify(body) }));
}

/** Clone a system theme into an editable user theme. */
export async function cloneLayoutTheme(id: string) {
  return readPayload<LayoutThemeData>(await fetch(`/api/layout/themes/${encodeURIComponent(id)}/clone`, { method: "POST" }));
}

/** Enable or disable a custom theme. */
export async function setLayoutThemeStatus(id: string, status: "enabled" | "disabled") {
  return readPayload<LayoutThemeData>(await fetch(`/api/layout/themes/${encodeURIComponent(id)}/status`, { method: "PATCH", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ status }) }));
}

/** Delete an unused custom theme. */
export async function deleteLayoutTheme(id: string) {
  const response = await fetch(`/api/layout/themes/${encodeURIComponent(id)}`, { method: "DELETE" });
  if (!response.ok) await readPayload<never>(response);
}

/** Render Markdown into the same sanitized HTML used for preview and copying. */
export async function renderPublishingMarkdown(markdown: string, themeVersionId?: string) {
  return readPayload<PublishingRenderData>(await fetch("/api/publishing/render", {
    method: "POST", headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ markdown, themeVersionId: themeVersionId || null }),
  }));
}

/** Load layout settings without creating an article version. */
export async function loadArticleLayout(articleId: string, documentId?: string | null) {
  return readPayload<ArticleLayoutData>(
    await fetch(articlesOrDocumentsPath(articleId, documentId, "/layout"), {
      cache: "no-store",
    }),
  );
}

/** Persist layout settings only after an explicit user action. */
export async function saveArticleLayout(
  articleId: string,
  body: {
    articleVersionNo: number;
    themeVersionId: string;
    options?: Record<string, unknown>;
    documentId?: string | null;
  },
) {
  const { documentId, ...rest } = body;
  return readPayload<ArticleLayoutData>(
    await fetch(articlesOrDocumentsPath(articleId, documentId, "/layout"), {
      method: "PUT",
      headers: { "Content-Type": "application/json" },
      // 仅在调用方显式给出 documentId 时写入
      body: JSON.stringify({
        ...rest,
        ...(documentId ? { documentId } : {}),
      }),
    }),
  );
}

/** Freeze the current rendered article as an immutable publication snapshot. */
export async function createPublishingSnapshot(
  articleId: string,
  body: {
    articleVersionNo: number;
    themeVersionId: string;
    options?: Record<string, unknown>;
    /** 统一文档 ID；课程侧通常等于 articleId */
    documentId?: string | null;
  },
) {
  const { documentId, ...rest } = body;
  return readPayload<PublishingSnapshotData>(
    await fetch(articlesOrDocumentsPath(articleId, documentId, "/publishing-snapshots"), {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      // 仅在调用方显式给出 documentId 时写入；否则交给后端按 articleId 解析
      body: JSON.stringify({
        ...rest,
        ...(documentId ? { documentId } : {}),
      }),
    }),
  );
}

/** Load the course index and dashboard metrics. */
export async function loadChengwenfangData<T>(themeId?: string) {
  const path = themeId
    ? `/api/themes/${encodeURIComponent(themeId)}/courses`
    : "/api/courses";
  return readPayload<T>(await fetch(path, { cache: "no-store" }));
}

/** Load fixed content modes and their selectable themes. */
export async function listContentModes() {
  return readPayload<ContentModeData[]>(await fetch("/api/modes", { cache: "no-store" }));
}

/** Load current draft and article content for one course. */
export async function loadCourseContent(courseId: string, themeId?: string) {
  const theme = themeId || "theme-ai-self-study-zero";
  const response = await fetch(
    `/api/themes/${encodeURIComponent(theme)}/courses/${encodeURIComponent(courseId)}/content`,
    { cache: "no-store" },
  );
  return readPayload<CourseContentData>(response);
}

/** Create a guided theme draft under a mode. */
export async function createThemeDraft(
  modeId: string,
  body?: { name?: string; description?: string; code?: string },
) {
  const response = await fetch(`/api/modes/${encodeURIComponent(modeId)}/themes/drafts`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body || {}),
  });
  return readPayload<ContentThemeData>(response);
}

/** Theme detail. */
export async function getContentTheme(themeId: string) {
  return readPayload<ContentThemeData>(
    await fetch(`/api/themes/${encodeURIComponent(themeId)}`, { cache: "no-store" }),
  );
}

/** Merge theme questionnaire briefing. */
export async function patchThemeBriefing(
  themeId: string,
  body: {
    briefing?: Record<string, unknown>;
    name?: string;
    description?: string;
    code?: string;
  },
) {
  const response = await fetch(`/api/themes/${encodeURIComponent(themeId)}/briefing`, {
    method: "PATCH",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body),
  });
  return readPayload<ContentThemeData>(response);
}

/** Enqueue curriculum generation job. */
export async function generateThemeCurriculum(themeId: string) {
  const response = await fetch(
    `/api/themes/${encodeURIComponent(themeId)}/curriculum:generate`,
    { method: "POST" },
  );
  return readPayload<ThemeCurriculumGenerateData>(response);
}

/** Poll curriculum generation job. */
export async function loadThemeCurriculumJob(themeId: string) {
  return readPayload<JobData | null>(
    await fetch(`/api/themes/${encodeURIComponent(themeId)}/curriculum-job`, {
      cache: "no-store",
    }),
  );
}

/** Load curriculum draft. */
export async function getCurriculumDraft(themeId: string) {
  return readPayload<CurriculumDraftData>(
    await fetch(`/api/themes/${encodeURIComponent(themeId)}/curriculum-draft`, {
      cache: "no-store",
    }),
  );
}

/** Save edited curriculum draft. */
export async function saveCurriculumDraft(themeId: string, draft: CurriculumDraftData) {
  const response = await fetch(
    `/api/themes/${encodeURIComponent(themeId)}/curriculum-draft`,
    {
      method: "PUT",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ draft }),
    },
  );
  return readPayload<CurriculumDraftData>(response);
}

/** Commit curriculum shells. */
export async function commitThemeCurriculum(themeId: string) {
  const response = await fetch(
    `/api/themes/${encodeURIComponent(themeId)}/curriculum:commit`,
    { method: "POST" },
  );
  return readPayload<ThemeCurriculumCommitData>(response);
}

/** Confirm a hot-news theme questionnaire and seed initial search channels. */
export async function commitHotNewsTheme(themeId: string) {
  const response = await fetch(
    `/api/themes/${encodeURIComponent(themeId)}/hot-news:commit`,
    { method: "POST" },
  );
  return readPayload<{
    themeId: string;
    status: string;
    channelCount: number;
    seededChannelTypes: string[];
  }>(response);
}

/** AI 总结热点主题短搜索词（失败时后端回退规则建议）。 */
export async function suggestHotNewsSearchQueries(body: {
  name?: string;
  description?: string;
  domainLabel?: string;
  mustWatch?: string[];
}) {
  const response = await fetch("/api/themes/hot-news:suggest-search-queries", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({
      name: body.name,
      description: body.description,
      domainLabel: body.domainLabel,
      mustWatch: body.mustWatch || [],
    }),
  });
  return readPayload<{ queries: string[]; fromAi: boolean }>(response);
}

/** 主题写作价值评分维度。 */
export type ScoringRubricDimension = {
  key: string;
  label: string;
  description: string;
  weight: number;
};

/** 主题写作价值评分量表。 */
export type ScoringRubric = {
  version?: number;
  generatedAt?: string;
  dimensions: ScoringRubricDimension[];
  scoreGuidance?: string;
};

/** AI 生成热点主题 GitHub 查询串（失败时 fromAi=false，不静默凑词）。 */
export async function suggestHotNewsGithubQuery(body: {
  name?: string;
  description?: string;
  domainLabel?: string;
  searchQueries?: string[];
}) {
  const response = await fetch("/api/themes/hot-news:suggest-github-query", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({
      name: body.name,
      description: body.description,
      domainLabel: body.domainLabel,
      searchQueries: body.searchQueries || [],
    }),
  });
  return readPayload<{ query: string; fromAi: boolean }>(response);
}

/** AI 生成热点主题评分量表（失败时后端回退启发式）。 */
export async function suggestHotNewsScoringRubric(body: {
  name?: string;
  description?: string;
  domainLabel?: string;
  searchQueries?: string[];
  worthWriting?: string[];
  readers?: string[];
  outOfScope?: string;
  contentStance?: string;
}) {
  const response = await fetch("/api/themes/hot-news:suggest-scoring-rubric", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({
      name: body.name,
      description: body.description,
      domainLabel: body.domainLabel,
      searchQueries: body.searchQueries || [],
      worthWriting: body.worthWriting || [],
      readers: body.readers || [],
      outOfScope: body.outOfScope,
      contentStance: body.contentStance,
    }),
  });
  return readPayload<{ rubric: ScoringRubric; fromAi: boolean }>(response);
}

/** Patch theme metadata / status. */
export async function patchContentTheme(
  themeId: string,
  body: { name?: string; description?: string; status?: string; sortOrder?: number },
) {
  const response = await fetch(`/api/themes/${encodeURIComponent(themeId)}`, {
    method: "PATCH",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body),
  });
  return readPayload<ContentThemeData>(response);
}

/** Soft-delete a theme. Courses and articles stay in the database. */
export async function deleteContentTheme(themeId: string) {
  const response = await fetch(`/api/themes/${encodeURIComponent(themeId)}`, {
    method: "DELETE",
  });
  return readPayload<null>(response);
}

/** List themes under a mode. includeDeleted returns only soft-deleted rows. */
export async function listContentThemes(modeId: string, includeDeleted = false) {
  const query = includeDeleted ? "?includeDeleted=true" : "";
  return readPayload<ContentThemeData[]>(
    await fetch(`/api/modes/${encodeURIComponent(modeId)}/themes${query}`, {
      cache: "no-store",
    }),
  );
}

/** Restore a soft-deleted theme (status becomes disabled). */
export async function restoreContentTheme(themeId: string) {
  const response = await fetch(`/api/themes/${encodeURIComponent(themeId)}/restore`, {
    method: "POST",
  });
  return readPayload<ContentThemeData>(response);
}

/** 热点主题工作区路径（雷达 / 已选题 / 来源）。 */
export function hotNewsThemePath(
  code: string,
  view: "radar" | "selected" | "sources" | "wizard",
) {
  return `/modes/hot-news/themes/${themePathSlug(code)}/${view}`;
}

/** 从主题 briefing/defaults JSON 解析默认搜索关键词（多行）。 */
export function parseHotNewsThemeSearchQueries(
  briefingJson?: string | null,
  defaultsJson?: string | null,
): string {
  const fromPayload = (raw?: string | null) => {
    if (!raw) return "";
    try {
      const parsed = JSON.parse(raw) as {
        radar?: { search_queries?: unknown };
        search_queries?: unknown;
      };
      const list = parsed.radar?.search_queries ?? parsed.search_queries;
      if (!Array.isArray(list)) return "";
      return list
        .map((item) => String(item || "").trim())
        .filter(Boolean)
        .join("\n");
    } catch {
      return "";
    }
  };
  return fromPayload(briefingJson) || fromPayload(defaultsJson);
}

/** 从主题 briefing/defaults JSON 解析 GitHub 默认查询串。 */
export function parseHotNewsThemeGithubQuery(
  briefingJson?: string | null,
  defaultsJson?: string | null,
): string {
  const fromPayload = (raw?: string | null) => {
    if (!raw) return "";
    try {
      const parsed = JSON.parse(raw) as {
        radar?: { github_query?: unknown };
        github_query?: unknown;
      };
      const value = parsed.radar?.github_query ?? parsed.github_query;
      return String(value || "").trim();
    } catch {
      return "";
    }
  };
  return fromPayload(briefingJson) || fromPayload(defaultsJson);
}

/** Import learning-content paths into MySQL stored_files / course_media. */
export async function syncLearningContent() {
  const response = await fetch("/api/migrations/sync-learning-content", { method: "POST" });
  return readPayload<{ ok: boolean; courses: number; media: number }>(response);
}

/** Save a versioned article edit and return the committed current state. */
export async function saveArticleContent(
  articleId: string,
  content: string,
  source = "manual_edit",
  reason = "手动编辑",
  documentId?: string | null,
) {
  const response = await fetch(articlesOrDocumentsPath(articleId, documentId, ""), {
    method: "PUT",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ content, source, reason }),
  });
  return readPayload<ArticleData>(response);
}

/** Load retained versions from newest to oldest. */
export async function loadArticleVersions(
  articleId: string,
  documentId?: string | null,
) {
  const response = await fetch(
    articlesOrDocumentsPath(articleId, documentId, "/versions"),
    { cache: "no-store" },
  );
  const versions = await readPayload<ArticleVersionData[]>(response);
  return versions.map((version) => ({
    ...version,
    reason: version.reason || "",
    audit: version.audit ?? { score: 0, passed: 0, warnings: 0, failed: 0 },
  }));
}

/** Restore a historical snapshot as a new current version. */
export async function restoreArticleVersion(
  articleId: string,
  versionId: string,
  documentId?: string | null,
) {
  const response = await fetch(
    articlesOrDocumentsPath(
      articleId,
      documentId,
      `/restore/${encodeURIComponent(versionId)}`,
    ),
    { method: "POST" },
  );
  return readPayload<ArticleData>(response);
}

/** Normalize backend AuditResultView into the frontend AuditReport shape. */
function normalizeAuditReport(articleId: string, report: Partial<AuditReportData> | null | undefined): AuditReportData {
  const items = Array.isArray(report?.items) ? report.items : [];
  return {
    id: report?.id || `audit-${articleId}`,
    articleId: report?.articleId || articleId,
    articleVersionId: report?.articleVersionId ?? null,
    type: report?.type || "deterministic",
    score: report?.score ?? 0,
    deterministicScore: report?.deterministicScore ?? report?.score ?? 0,
    semanticScore: report?.semanticScore ?? null,
    passed: report?.passed ?? 0,
    warnings: report?.warnings ?? 0,
    failed: report?.failed ?? 0,
    summary: report?.summary || "",
    createdAt: report?.createdAt || new Date().toISOString(),
    items,
  };
}

/**
 * 课程轨统一走 /api/documents/{id}（documentId 与 articleId 同值；缺 documentId 时用 articleId）。
 */
function articlesOrDocumentsPath(
  articleId: string,
  documentId: string | null | undefined,
  suffix = "",
) {
  const id = documentId || articleId;
  return `/api/documents/${encodeURIComponent(id)}${suffix}`;
}

/** Run and persist a fresh deterministic audit. */
export async function runArticleAudit(articleId: string, documentId?: string | null) {
  const response = await fetch(articlesOrDocumentsPath(articleId, documentId, "/audit"), {
    method: "POST",
  });
  return normalizeAuditReport(articleId, await readPayload<AuditReportData>(response));
}

/** Repair deterministic image metadata and return the fresh audit report. */
export async function repairArticleMetadata(articleId: string, documentId?: string | null) {
  const response = await fetch(
    articlesOrDocumentsPath(articleId, documentId, "/repair-metadata"),
    { method: "POST" },
  );
  return normalizeAuditReport(articleId, await readPayload<AuditReportData>(response));
}

/** Load the human decision for the current article version; stale decisions return null. */
export async function loadHumanReview(articleId: string, documentId?: string | null) {
  const response = await fetch(
    articlesOrDocumentsPath(articleId, documentId, "/human-review"),
    { cache: "no-store" },
  );
  if (response.status === 404) return null;
  return readPayload<HumanReviewData>(response);
}

/** Save an approval or rejection against the article version currently in the database. */
export async function saveHumanReview(
  articleId: string,
  status: "approved" | "rejected",
  comment: string,
  documentId?: string | null,
) {
  const response = await fetch(
    articlesOrDocumentsPath(articleId, documentId, "/human-review"),
    {
      method: "PUT",
      headers: { "Content-Type": "application/json" },
      // 仅在调用方显式给出 documentId 时写入
      body: JSON.stringify({
        status,
        comment,
        ...(documentId ? { documentId } : {}),
      }),
    },
  );
  return readPayload<HumanReviewData>(response);
}

/** Withdraw the current version's human decision without touching older review history. */
export async function resetHumanReview(articleId: string, documentId?: string | null) {
  const response = await fetch(
    articlesOrDocumentsPath(articleId, documentId, "/human-review"),
    { method: "DELETE" },
  );
  if (!response.ok) return readPayload<{ ok: boolean }>(response);
  return { ok: true };
}

/** Migrate legacy browser decisions once, deleting only keys confirmed by the backend. */
export async function migrateLegacyHumanReviews(courses: Array<{ id: string }>) {
  const entries: Array<{ courseId: string; status: "approved" | "rejected"; comment: string; reviewedAt?: string }> = [];
  for (const course of courses) {
    const key = `chengwenfang-review:${course.id}`;
    const stored = window.localStorage.getItem(key);
    if (!stored) continue;
    try {
      const review = JSON.parse(stored) as { status?: "approved" | "rejected"; comment?: string; reviewedAt?: string };
      if (review.status === "approved" || review.status === "rejected") {
        entries.push({ courseId: course.id, status: review.status, comment: review.comment ?? "", reviewedAt: review.reviewedAt });
      }
    } catch {
      // Keep malformed legacy data for manual inspection.
    }
  }
  if (!entries.length) return 0;
  const response = await fetch("/api/migrations/human-reviews", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ entries })
  });
  const result = await readPayload<{ migratedKeys: string[]; skippedKeys: string[] }>(response);
  for (const key of result.migratedKeys) window.localStorage.removeItem(key);
  return result.migratedKeys.length;
}

/** Queue a repair and return only after its article and post-audit commit together. */
export async function repairArticle(
  articleId: string,
  onProgress?: (event: JobEventData) => void,
  documentId?: string | null,
) {
  const response = await fetch(articlesOrDocumentsPath(articleId, documentId, "/repair"), {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    // 仅在调用方显式给出 documentId 时写入
    body: JSON.stringify(documentId ? { documentId } : {}),
  });
  const job = await readPayload<JobData>(response);
  window.localStorage.setItem(`chengwenfang-active-repair:${articleId}`, job.id);
  await waitForBackgroundJob(job.id, onProgress);
  const result = await readPayload<RepairResponseData>(
    await fetch(`/api/jobs/${encodeURIComponent(job.id)}/result`, { cache: "no-store" })
  );
  window.localStorage.removeItem(`chengwenfang-active-repair:${articleId}`);
  return result;
}

/** Resume an unfinished repair after a page refresh when a local task pointer exists. */
export async function resumeArticleRepair(
  articleId: string,
  onProgress?: (event: JobEventData) => void,
  onStarted?: () => void,
) {
  const key = `chengwenfang-active-repair:${articleId}`;
  const jobId = window.localStorage.getItem(key);
  if (!jobId) return null;
  try {
    const job = await readPayload<JobData>(
      await fetch(`/api/jobs/${encodeURIComponent(jobId)}`, { cache: "no-store" })
    );
    if (job.type !== "article_repair") {
      window.localStorage.removeItem(key);
      return null;
    }
    if (job.status === "succeeded") {
      const result = await readPayload<RepairResponseData>(
        await fetch(`/api/jobs/${encodeURIComponent(jobId)}/result`, { cache: "no-store" })
      );
      window.localStorage.removeItem(key);
      return result;
    }
    if (job.status !== "pending" && job.status !== "running") {
      window.localStorage.removeItem(key);
      throw new Error(job.errorMessage || "AI 修复任务未完成");
    }
    onStarted?.();
    await waitForBackgroundJob(jobId, onProgress);
    const result = await readPayload<RepairResponseData>(
      await fetch(`/api/jobs/${encodeURIComponent(jobId)}/result`, { cache: "no-store" })
    );
    window.localStorage.removeItem(key);
    return result;
  } catch (error) {
    window.localStorage.removeItem(key);
    throw error;
  }
}

/** Queue an image job using the same durable worker and SSE channel as repair. */
export async function requestImageGeneration(
  articleId: string,
  request: {
    kind?: "cover" | "inline";
    prompt: string;
    size?: string;
    placeholder?: string;
    documentId?: string | null;
  },
  onProgress?: (event: JobEventData) => void
) {
  const { documentId, ...rest } = request;
  const response = await fetch(
    articlesOrDocumentsPath(articleId, documentId, "/images"),
    {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      // 仅在调用方显式给出 documentId 时写入；否则交给后端按 articleId 解析
      body: JSON.stringify({
        ...rest,
        ...(documentId ? { documentId } : {}),
      }),
    },
  );
  const job = await readPayload<JobData>(response);
  const completed = await waitForBackgroundJob(job.id, onProgress);
  if (completed.status !== "succeeded") {
    throw new Error(completed.errorMessage || "图片生成失败。");
  }
  return completed.output as {
    id: string;
    publicUrl: string;
    contentHash: string;
    width?: number;
    height?: number;
  };
}

/** Delete one generated image and all persisted article references. */
export async function deleteGeneratedImage(imageId: string) {
  const response = await fetch(`/api/images/${encodeURIComponent(imageId)}`, {
    method: "DELETE"
  });
  if (response.status === 204) return;
  await readPayload<unknown>(response);
}

export async function requestContentGeneration(
  articleId: string,
  kind: "course_draft" | "wechat_article",
  onProgress: (event: { progress: number; message: string }) => void,
  documentId?: string | null,
) {
  const response = await fetch(
    `/api/articles/${encodeURIComponent(articleId)}/generation/${kind}`,
    {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      // 仅在调用方显式给出 documentId 时写入
      body: JSON.stringify(documentId ? { documentId } : {}),
    },
  );
  const job = await readPayload<JobData>(response);
  const completed = await waitForBackgroundJob(job.id, onProgress);
  if (completed.status !== "succeeded") throw new Error(completed.errorMessage || "内容生成失败");
  return completed.output;
}

/** Run the complete course-content workflow from one durable task. */
export async function requestCourseContentGeneration(
  courseId: string,
  settings: Pick<CourseGenerationSettingsData, "wordCountOption" | "imageCountOption">,
  onProgress: (event: JobEventData) => void,
  onQueued?: (job: JobData) => void,
) {
  const response = await fetch(`/api/courses/${encodeURIComponent(courseId)}/generate-content`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(settings),
  });
  const job = await readPayload<JobData>(response);
  onQueued?.(job);
  const completed = await waitForBackgroundJob(job.id, onProgress);
  if (completed.status !== "succeeded") throw new Error(completed.errorMessage || "课程内容生成失败");
  return completed.output;
}

export async function loadCourseGenerationSettings(courseId: string) {
  return readPayload<CourseGenerationSettingsData>(
    await fetch(`/api/courses/${encodeURIComponent(courseId)}/generation-settings`, { cache: "no-store" }),
  );
}

export async function saveCourseGenerationSettings(
  courseId: string,
  settings: Pick<CourseGenerationSettingsData, "wordCountOption" | "imageCountOption">,
) {
  return readPayload<CourseGenerationSettingsData>(
    await fetch(`/api/courses/${encodeURIComponent(courseId)}/generation-settings`, {
      method: "PUT",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(settings),
    }),
  );
}

/** Load newest-first unified generation records for the independent trajectory page. */
export async function listCourseGenerationJobs(courseId?: string) {
  const query = courseId ? `?course_id=${encodeURIComponent(courseId)}` : "";
  return readPayload<JobData[]>(
    await fetch(`/api/course-generation-jobs${query}`, { cache: "no-store" }),
  );
}

export async function setCoursePublished(courseId: string, published: boolean) {
  const response = await fetch(`/api/courses/${encodeURIComponent(courseId)}/lifecycle`, { method: "PATCH", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ published }) });
  return readPayload<{ courseId: string; published: boolean }>(response);
}

/** Course fields needed to discover legacy browser storage keys. */
type LegacyCourse = { id: string; contentHash?: string };
/** Browser-local version shape used only by the one-time migration. */
type LegacyVersion = {
  id?: string;
  createdAt?: string;
  reason?: string;
  content: string;
};

/** Move legacy browser content into the database before deleting localStorage keys. */
export async function migrateLegacyBrowserContent(courses: LegacyCourse[]) {
  type MigrationEntry = {
    courseId: string;
    kind: "draft" | "article";
    content?: string;
    versions?: LegacyVersion[];
    storageKeys: string[];
  };
  const grouped = new Map<string, MigrationEntry>();
  const storageKeys = Array.from(
    { length: window.localStorage.length },
    (_, index) => window.localStorage.key(index)
  ).filter((key): key is string => Boolean(key));

  /** Return or create the migration accumulator for one course article. */
  function entryFor(courseId: string, kind: "draft" | "article") {
    const key = `${courseId}:${kind}`;
    const existing = grouped.get(key);
    if (existing) return existing;
    const entry: MigrationEntry = { courseId, kind, storageKeys: [] };
    grouped.set(key, entry);
    return entry;
  }

  for (const course of courses) {
    for (const kind of ["draft", "article"] as const) {
      const prefix = `chengwenfang-content:${course.id}:`;
      const suffix = `:${kind}`;
      const candidates = storageKeys
        .filter((key) => key.startsWith(prefix) && key.endsWith(suffix))
        .sort();
      const expected = `chengwenfang-content:${course.id}:${course.contentHash ?? "nohash"}:${kind}`;
      const selected = candidates.includes(expected) ? expected : candidates.at(-1);
      if (selected) {
        const entry = entryFor(course.id, kind);
        entry.content = window.localStorage.getItem(selected) ?? "";
        entry.versions = [
          ...(entry.versions ?? []),
          ...candidates
            .filter((key) => key !== selected)
            .map((key) => ({
              reason: "旧浏览器正文",
              content: window.localStorage.getItem(key) ?? ""
            }))
        ];
        entry.storageKeys.push(...candidates);
      }
    }

    const versionKey = `chengwenfang-versions:${course.id}`;
    const storedVersions = window.localStorage.getItem(versionKey);
    if (storedVersions) {
      try {
        const entry = entryFor(course.id, "article");
        entry.versions = [
          ...(entry.versions ?? []),
          ...(JSON.parse(storedVersions) as LegacyVersion[])
        ];
        entry.storageKeys.push(versionKey);
      } catch {
        // Keep invalid legacy data for manual recovery instead of deleting it.
      }
    }
  }

  const entries = [...grouped.values()];
  if (!entries.length) return 0;
  // 浏览器本地迁移接口已下线：仅清理本地键，不再调用后端空实现。
  for (const entry of entries) {
    for (const key of entry.storageKeys) window.localStorage.removeItem(key);
  }
  return entries.length;
}
