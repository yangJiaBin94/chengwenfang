import { unwrapApiResult } from "../../shared/api/http";
import {
  loadJob,
  waitForBackgroundJob,
  type JobData,
  type WorkflowNodeData,
} from "../../shared/api/jobs";
import type {
  ChannelFetchConfig,
  ChannelTypeValue,
  ResearchItemScoreDetails,
  TopicScoreDetails,
} from "./model";
import {
  HOT_NEWS_DOCUMENT_KIND_DRAFT,
  HOT_NEWS_DOCUMENT_KIND_WECHAT,
  type HotNewsDocumentKind,
  resolveHotNewsDocumentId,
} from "./model";

export type { ResearchItemScoreDetails };

/** 资料池素材条目（与后端 HotNewsResearchItemView 对齐的常用字段）。 */
export type HotNewsResearchItem = {
  id: string;
  topicId?: string;
  title?: string;
  url?: string;
  summary?: string;
  contentSummary?: string;
  score?: number;
  overallScore?: number;
  scoreDetails?: ResearchItemScoreDetails | null;
  kind?: string;
  sourceLabel?: string;
  siteLabel?: string;
  mediaLabel?: string;
  videoParseStatus?: string | null;
  videoParseStep?: string | null;
  videoParseError?: string | null;
  videoContentKind?: string | null;
  /** 视频时长（秒）；未探测到时为空 */
  videoDurationSeconds?: number | null;
  fetchedAt?: string | null;
  createdAt?: string | null;
};

export type HotNewsSource = {
  id: string;
  themeId: string;
  name: string;
  type: ChannelTypeValue;
  baseUrl: string;
  enabled: boolean;
  config: ChannelFetchConfig;
  credentialConfigured: boolean;
  lastSuccessAt?: string | null;
  deletedAt?: string | null;
  createdAt: string;
  updatedAt: string;
};

export type HotNewsSourceTest = {
  success: boolean;
  itemCount: number;
  message: string;
  checkedAt: string;
};

export type HotNewsRun = {
  id: string;
  themeId: string;
  status: string;
  trigger: string;
  requestedChannels: string[];
  startedAt?: string | null;
  finishedAt?: string | null;
  totalItems: number;
  newItems: number;
  dedupedItems: number;
  errorSummary?: string | null;
  channelRuns: HotNewsChannelRun[];
  createdAt: string;
};

export type HotNewsChannelRun = {
  id: string;
  channelId: string;
  channelName: string;
  channelType: string;
  status: string;
  itemsCount: number;
  newItems: number;
  errorCode?: string | null;
  errorMessage?: string | null;
  startedAt?: string | null;
  finishedAt?: string | null;
};

export type HotNewsTopic = {
  id: string;
  themeId: string;
  title: string;
  summary: string;
  status: string;
  heatScore: number;
  writingScore: number;
  confidenceScore: number;
  velocityScore: number;
  freshnessScore: number;
  evidenceScore: number;
  lifecycle: string;
  scoreDetails: TopicScoreDetails;
  scoreReason: string;
  sourceCount: number;
  firstSeenAt: string;
  lastSeenAt: string;
  /** 成员信源最早发布时间 */
  publishFrom?: string | null;
  /** 成员信源最晚发布时间 */
  publishTo?: string | null;
  expiresAt?: string | null;
  createdAt: string;
  updatedAt: string;
  /** 确认选题时间 */
  confirmedAt?: string | null;
  /** 雷达采集批次 */
  radarBatchId?: string | null;
  /** 本批次名次 */
  radarRank?: number | null;
  /** 成员来源站标签 */
  siteLabels?: string[] | null;
  /** 成员媒体类型标签（文章/视频） */
  mediaTypes?: string[] | null;
  /** 选题来源形态（如 ai_theme_seed） */
  originKind?: string | null;
};

export type HotNewsTopicSource = {
  id: string;
  topicId: string;
  sourceItemId: string;
  role: string;
  title: string;
  /** 信源事实速览 / AI 概要 */
  summary?: string | null;
  url: string;
  sourceName: string;
  /** 站点来源展示名，如 B站 / YouTube */
  siteLabel?: string | null;
  /** article | video */
  mediaType?: string | null;
  lifecycleStatus?: string | null;
  publishedAt?: string | null;
};

export type HotNewsEvidence = {
  id: string;
  topicId: string;
  statement: string;
  evidenceType: string;
  sourceItemId?: string | null;
  sourceTitle?: string | null;
  sourceUrl?: string | null;
  sourceName?: string | null;
  siteLabel?: string | null;
  mediaType?: string | null;
  /** 关联信源发布时间 */
  publishedAt?: string | null;
  status: string;
  reviewer: string;
  note: string;
  /** 兼容摘录字段，常与 note 同为事实速览 */
  snippet?: string | null;
  createdAt: string;
};

export type HotNewsBrief = {
  id: string;
  topicId: string;
  status: string;
  title: string;
  articleType: string;
  targetAudience: string;
  angle: string;
  summary: {
    overview?: string;
    attentionReason?: string;
    confirmedFacts?: string[];
    unknowns?: string[];
    readerImpact?: string[];
    authorJudgementBoundary?: string;
    recommendedAngles?: string[];
    mustNotClaim?: string[];
    briefGate?: string;
    [key: string]: unknown;
  };
  sourceSnapshot: Array<Record<string, unknown>>;
  confirmationNote?: string | null;
  createdAt: string;
  updatedAt: string;
  confirmedAt?: string | null;
};

export type HotNewsArticle = {
  id: string;
  /** 公众号轨统一文档 ID（发布/配图下游） */
  documentId?: string | null;
  /** 草稿轨统一文档 ID */
  draftDocumentId?: string | null;
  topicId: string;
  briefId: string;
  title: string;
  draftContent?: string | null;
  draftContentHash?: string | null;
  draftWordCount: number;
  content: string;
  contentHash: string;
  wordCount: number;
  currentVersionNo: number;
  status: string;
  workflowRunId?: string | null;
  sourceSnapshot: Array<Record<string, unknown>>;
  generationSettings: {
    wordCountOption?: string;
    imageCountOption?: string;
    illustrationStyle?: string;
    coverBrief?: string;
    workingTitle?: string;
  };
  createdAt: string;
  updatedAt: string;
};

export type HotNewsArticleVersion = {
  id: string;
  articleId: string;
  versionNo: number;
  draftContent?: string | null;
  draftContentHash?: string | null;
  content: string;
  contentHash: string;
  source?: string | null;
  reason?: string | null;
  workflowRunId?: string | null;
  generationSettings: Record<string, unknown>;
  createdAt: string;
};
export type HotNewsAudit = {
  id: string;
  articleId: string;
  contentHash: string;
  status: string;
  ruleStatus?: string;
  aiStatus?: string;
  passedCount?: number;
  warningCount?: number;
  failedCount?: number;
  blockingCount?: number;
  repairableCount?: number;
  standardVersion?: string;
  passed?: number;
  warnings?: number;
  failed?: number;
  summary?: string;
  createdAt?: string;
  items: Array<{
    id: string;
    code: string;
    engine?: "rule" | "ai" | string;
    category?: string;
    severity: string;
    title: string;
    detail: string;
    location?: string | null;
    locations?: Array<Record<string, unknown>>;
    evidence?: Array<Record<string, unknown>>;
    repairable?: boolean;
    requiresHumanConfirmation?: boolean;
    repairStatus?: string | null;
    repairInstruction?: string | null;
    blocking: boolean;
    status: string;
    waiverReason?: string | null;
  }>;
};
export type HotNewsHumanReview = {
  id: string;
  articleId: string;
  contentHash: string;
  status: string;
  comment: string;
  reviewedAt: string;
};
export type HotNewsPublishingSnapshot = {
  id: string;
  articleId: string;
  contentHash: string;
  themeVersionId: string;
  html: string;
  htmlHash: string;
  diagnostics: Array<Record<string, unknown>>;
  createdAt: string;
};
export type HotNewsGeneratedImage = {
  id: string;
  articleId: string;
  kind: "cover" | "inline";
  sequenceNo?: number | null;
  prompt: string;
  publicUrl: string;
  width: number;
  height: number;
  contentHash: string;
  createdAt: string;
};

async function read<T>(response: Response): Promise<T> {
  return unwrapApiResult<T>(response);
}

async function fetchWithRetry(
  input: RequestInfo | URL,
  init?: RequestInit,
): Promise<Response> {
  let lastError: unknown;
  for (let attempt = 0; attempt < 4; attempt += 1) {
    try {
      return await fetch(input, init);
    } catch (error) {
      lastError = error;
      if (attempt < 3)
        await new Promise((resolve) => window.setTimeout(resolve, 800));
    }
  }
  throw lastError;
}

export function listHotNewsSources(themeId: string) {
  return fetch(`/api/themes/${encodeURIComponent(themeId)}/hot-news/channels`, {
    cache: "no-store",
  }).then((r) => read<HotNewsSource[]>(r));
}

/** 来源管理分页结果。 */
export type HotNewsSourcePage = {
  items: HotNewsSource[];
  total: number;
  page: number;
  pageSize: number;
  enabledCount: number;
  disabledCount: number;
};

/** 按主题分页查询来源（后端 LIMIT/OFFSET，含筛选）。 */
export function pageHotNewsSources(
  themeId: string,
  params: {
    page?: number;
    pageSize?: number;
    keyword?: string;
    type?: HotNewsSource["type"] | "all";
    enabled?: boolean | null;
  } = {},
) {
  const search = new URLSearchParams();
  search.set("page", String(params.page ?? 1));
  search.set("pageSize", String(params.pageSize ?? 5));
  const keyword = params.keyword?.trim();
  if (keyword) search.set("keyword", keyword);
  if (params.type && params.type !== "all") search.set("type", params.type);
  if (params.enabled === true || params.enabled === false) {
    search.set("enabled", String(params.enabled));
  }
  return fetch(
    `/api/themes/${encodeURIComponent(themeId)}/hot-news/channels/page?${search.toString()}`,
    { cache: "no-store" },
  ).then((r) => read<HotNewsSourcePage>(r));
}

export function createHotNewsSource(
  themeId: string,
  body: {
    name: string;
    type: HotNewsSource["type"];
    baseUrl: string;
    config?: ChannelFetchConfig;
  },
) {
  return fetch(`/api/themes/${encodeURIComponent(themeId)}/hot-news/channels`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body),
  }).then((r) => read<HotNewsSource>(r));
}

/** AI 生成来源时跳过的候选。 */
export type HotNewsSourceChannelSkipped = {
  name: string;
  type: string;
  baseUrl: string;
  reason: string;
};

/** AI 生成来源渠道结果。 */
export type HotNewsSourceChannelGenerateResult = {
  created: HotNewsSource[];
  skipped: HotNewsSourceChannelSkipped[];
  fromAi: boolean;
};

/** 按主题 AI 生成来源：后端校验连通性后仅落库可用项。 */
export function generateHotNewsSourcesByAi(themeId: string) {
  return fetch(
    `/api/themes/${encodeURIComponent(themeId)}/hot-news/channels:generate-ai`,
    { method: "POST" },
  ).then((r) => read<HotNewsSourceChannelGenerateResult>(r));
}

export function patchHotNewsSource(
  id: string,
  body: {
    name?: string;
    type?: HotNewsSource["type"];
    baseUrl?: string;
    enabled?: boolean;
    config?: ChannelFetchConfig;
  },
) {
  return fetch(`/api/hot-news/channels/${encodeURIComponent(id)}`, {
    method: "PATCH",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body),
  }).then((r) => read<HotNewsSource>(r));
}

export function deleteHotNewsSource(id: string) {
  return fetch(`/api/hot-news/channels/${encodeURIComponent(id)}`, {
    method: "DELETE",
  }).then((r) => read<{ id: string; deletedAt: string }>(r));
}

export function testHotNewsSource(id: string) {
  return fetch(`/api/hot-news/channels/${encodeURIComponent(id)}/test`, {
    method: "POST",
  }).then((r) => read<HotNewsSourceTest>(r));
}

export function startHotNewsFetch(themeId: string, channelIds?: string[]) {
  return fetch(
    `/api/themes/${encodeURIComponent(themeId)}/hot-news/fetch-runs`,
    {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ channelIds: channelIds || null, hours: 72 }),
    },
  ).then((r) => read<JobData>(r));
}

export function listHotNewsRuns(themeId: string) {
  return fetch(
    `/api/themes/${encodeURIComponent(themeId)}/hot-news/fetch-runs`,
    { cache: "no-store" },
  ).then((r) => read<HotNewsRun[]>(r));
}

/** 主题下雷达采集 FormalGraph Job 列表（执行记录页）。 */
export function listHotNewsRadarCollectRuns(themeId: string) {
  return fetch(
    `/api/themes/${encodeURIComponent(themeId)}/hot-news/collect-runs`,
    { cache: "no-store" },
  ).then((r) => read<JobData[]>(r));
}

export function listHotNewsTopics(themeId: string, status?: string) {
  const params = new URLSearchParams();
  if (status) params.set("status", status);
  const query = params.toString() ? `?${params.toString()}` : "";
  return fetch(
    `/api/themes/${encodeURIComponent(themeId)}/hot-news/topics${query}`,
    { cache: "no-store" },
  ).then((r) => read<HotNewsTopic[]>(r));
}

/** 热点雷达选题筛选。 */
export type HotNewsRadarTopicFilter =
  | "today"
  | "all"
  | "candidate"
  | "confirmed";

/** 热点雷达选题排序。 */
export type HotNewsRadarTopicSort = "writing" | "heat";

/** 热点雷达选题分页结果。 */
export type HotNewsTopicPage = {
  items: HotNewsTopic[];
  total: number;
  page: number;
  pageSize: number;
  candidateCount: number;
  topicCount: number;
};

/** 按主题分页查询雷达选题（筛选/关键字/排序在服务端）。 */
export function pageHotNewsTopics(
  themeId: string,
  params: {
    page?: number;
    pageSize?: number;
    keyword?: string;
    filter?: HotNewsRadarTopicFilter;
    sortBy?: HotNewsRadarTopicSort;
  } = {},
) {
  const search = new URLSearchParams();
  search.set("page", String(params.page ?? 1));
  search.set("pageSize", String(params.pageSize ?? 5));
  const keyword = params.keyword?.trim();
  if (keyword) search.set("keyword", keyword);
  if (params.filter) search.set("filter", params.filter);
  if (params.sortBy) search.set("sortBy", params.sortBy);
  return fetch(
    `/api/themes/${encodeURIComponent(themeId)}/hot-news/topics/page?${search.toString()}`,
    { cache: "no-store" },
  ).then((r) => read<HotNewsTopicPage>(r));
}

/** 已选题页：全部已确认（含写作中、已完成），不限当日。 */
export function listHotNewsConfirmedTopics(themeId: string) {
  return fetch(
    `/api/themes/${encodeURIComponent(themeId)}/hot-news/confirmed-topics`,
    { cache: "no-store" },
  ).then((r) => read<HotNewsTopic[]>(r));
}

export function getHotNewsTopic(id: string) {
  return fetch(`/api/hot-news/topics/${encodeURIComponent(id)}`, {
    cache: "no-store",
  }).then((r) => read<HotNewsTopic>(r));
}

export function updateHotNewsTopicStatus(id: string, status: string) {
  return fetch(`/api/hot-news/topics/${encodeURIComponent(id)}/status`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ status }),
  }).then((r) => read<HotNewsTopic>(r));
}
export function startHotNewsTopicResearch(topicId: string) {
  return fetch(
    `/api/hot-news/topics/${encodeURIComponent(topicId)}/research-runs`,
    { method: "POST" },
  ).then((r) => read<JobData>(r));
}
export function listHotNewsResearchRuns(topicId: string) {
  return fetchWithRetry(
    `/api/hot-news/topics/${encodeURIComponent(topicId)}/research-runs`,
    { cache: "no-store" },
  ).then((r) => read<Array<Record<string, unknown>>>(r));
}
export function listHotNewsResearchItems(topicId: string) {
  return fetchWithRetry(
    `/api/hot-news/topics/${encodeURIComponent(topicId)}/research-items`,
    { cache: "no-store" },
  ).then((r) => read<Array<Record<string, unknown>>>(r));
}

/** 选题资料池分页结果。 */
export type HotNewsResearchItemPage = {
  items: Array<Record<string, unknown>>;
  total: number;
  page: number;
  pageSize: number;
  itemCount: number;
  articleCount: number;
  videoCount: number;
  pendingVideoCount: number;
};

/** 按选题分页查询资料池（后端 LIMIT/OFFSET，含关键字与形态筛选）。 */
export function pageHotNewsResearchItems(
  topicId: string,
  params: {
    page?: number;
    pageSize?: number;
    keyword?: string;
    media?: "all" | "article" | "video";
  } = {},
) {
  const search = new URLSearchParams();
  search.set("page", String(params.page ?? 1));
  search.set("pageSize", String(params.pageSize ?? 5));
  const keyword = params.keyword?.trim();
  if (keyword) search.set("keyword", keyword);
  if (params.media && params.media !== "all") search.set("media", params.media);
  return fetchWithRetry(
    `/api/hot-news/topics/${encodeURIComponent(topicId)}/research-items/page?${search.toString()}`,
    { cache: "no-store" },
  ).then((r) => read<HotNewsResearchItemPage>(r));
}
/** 手动添加资料：后端访问 URL 补全标题/摘要与标签。 */
export function addHotNewsManualResearchItem(topicId: string, url: string) {
  return fetch(
    `/api/hot-news/topics/${encodeURIComponent(topicId)}/research-items`,
    {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ url }),
    },
  ).then((r) => read<Record<string, unknown>>(r));
}
/** 批量或指定条目启动视频解析 Job。 */
export function startHotNewsVideoParse(
  topicId: string,
  body?: { itemIds?: string[]; fromStep?: string },
) {
  return fetch(
    `/api/hot-news/topics/${encodeURIComponent(topicId)}/research-items/video-parse`,
    {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(body || {}),
    },
  ).then((r) => read<JobData>(r));
}
/** 单条视频解析（可指定 fromStep 重试）。 */
export function startHotNewsVideoParseItem(
  topicId: string,
  itemId: string,
  body?: { fromStep?: string },
) {
  return fetch(
    `/api/hot-news/topics/${encodeURIComponent(topicId)}/research-items/${encodeURIComponent(itemId)}/video-parse`,
    {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(body || {}),
    },
  ).then((r) => read<JobData>(r));
}
export function listHotNewsResearchConflicts(topicId: string) {
  return fetchWithRetry(
    `/api/hot-news/topics/${encodeURIComponent(topicId)}/research-conflicts`,
    { cache: "no-store" },
  ).then((r) => read<Array<Record<string, unknown>>>(r));
}
export type HotNewsArticleSkeleton = {
  topicId: string;
  workingTitle?: string | null;
  scope?: string | null;
  coreThesis?: string | null;
  coreEvent?: string | null;
  keyInsights?: string[];
  impacts?: string[];
  readerSignals?: string[];
  narrativeShape?: string | null;
  sectionPlan?: string[];
  illustrationStyle?: string | null;
  coverBrief?: string | null;
  /** 骨架配图 Skill 写入的生图硬性要求，成文时拼进插图提示词 */
  imagePromptRequirements?: string | null;
  recommendedWordCountOption?: string | null;
  recommendedImageCountOption?: string | null;
  architecture?: string[];
  flow?: string[];
  modules?: Array<Record<string, unknown>>;
  referenceHints?: string[];
  updatedAt?: string | null;
  raw?: Record<string, unknown> | null;
};

export function getHotNewsArticleSkeleton(topicId: string) {
  return fetchWithRetry(
    `/api/hot-news/topics/${encodeURIComponent(topicId)}/article-skeleton`,
    { cache: "no-store" },
  ).then((r) => read<HotNewsArticleSkeleton | null>(r));
}

export function buildHotNewsArticleSkeleton(topicId: string) {
  return fetch(
    `/api/hot-news/topics/${encodeURIComponent(topicId)}/article-skeleton`,
    { method: "POST" },
  ).then((r) => read<HotNewsArticleSkeleton>(r));
}

export function listHotNewsTopicSources(id: string) {
  return fetchWithRetry(
    `/api/hot-news/topics/${encodeURIComponent(id)}/sources`,
    {
      cache: "no-store",
    },
  ).then((r) => read<HotNewsTopicSource[]>(r));
}

export function listHotNewsEvidence(id: string) {
  return fetchWithRetry(
    `/api/hot-news/topics/${encodeURIComponent(id)}/evidence`,
    {
      cache: "no-store",
    },
  ).then((r) => read<HotNewsEvidence[]>(r));
}

export function listHotNewsBriefs(id: string) {
  return fetchWithRetry(
    `/api/hot-news/topics/${encodeURIComponent(id)}/briefs`,
    {
      cache: "no-store",
    },
  ).then((r) => read<HotNewsBrief[]>(r));
}

export function createHotNewsBrief(id: string) {
  return fetch(`/api/hot-news/topics/${encodeURIComponent(id)}/briefs`, {
    method: "POST",
  }).then((r) => read<HotNewsBrief>(r));
}

export function confirmHotNewsBrief(id: string, note?: string) {
  return fetch(`/api/hot-news/briefs/${encodeURIComponent(id)}/confirm`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ note: note || null }),
  }).then((r) => read<HotNewsBrief>(r));
}

export function generateHotNewsArticle(briefId: string) {
  return fetch(
    `/api/hot-news/briefs/${encodeURIComponent(briefId)}/generate-article`,
    { method: "POST" },
  ).then((r) => read<JobData>(r));
}

export function generateHotNewsArticleByTopic(topicId: string) {
  return fetch(
    `/api/hot-news/topics/${encodeURIComponent(topicId)}/generate-article`,
    { method: "POST" },
  ).then((r) => read<JobData>(r));
}

/** 确保选题下存在文章壳（不入队生成）。 */
export function ensureHotNewsArticle(topicId: string) {
  return fetch(
    `/api/hot-news/topics/${encodeURIComponent(topicId)}/ensure-article`,
    { method: "POST" },
  ).then((r) => read<HotNewsArticle>(r));
}

export function listHotNewsArticles(topicId: string) {
  return fetchWithRetry(
    `/api/hot-news/topics/${encodeURIComponent(topicId)}/articles`,
    {
      cache: "no-store",
    },
  ).then((r) => read<HotNewsArticle[]>(r));
}

/**
 * 热点文章相关写读：有 documentId 走 /api/documents；否则回落 /api/hot-news/articles。
 *
 * @param articleId 热点文章 id
 * @param documentId 显式产出 id（可空）
 * @param suffix 路径后缀
 * @param kind 轨 kind，默认 wechat
 */
function hotArticlesOrDocumentsPath(
  articleId: string,
  documentId: string | null | undefined,
  suffix = "",
  kind: HotNewsDocumentKind = HOT_NEWS_DOCUMENT_KIND_WECHAT,
) {
  const resolved = resolveHotNewsDocumentId(articleId, documentId, kind);
  if (resolved) {
    return `/api/documents/${encodeURIComponent(resolved)}${suffix}`;
  }
  return `/api/hot-news/articles/${encodeURIComponent(articleId)}${suffix}`;
}

/**
 * 从 URL（`?documentId=` / `?job=`）或已知 fallback 解析热点公众号 documentId。
 *
 * @param fallback 已有 documentId（优先）
 */
export async function resolveHotNewsDocumentIdFromLocation(
  fallback?: string | null,
): Promise<string | null> {
  if (fallback) {
    return fallback;
  }
  if (typeof window === "undefined") {
    return null;
  }
  const params = new URLSearchParams(window.location.search);
  const fromQuery = params.get("documentId");
  if (fromQuery) {
    return fromQuery;
  }
  const jobId = params.get("job");
  if (!jobId) {
    return null;
  }
  try {
    const job = await loadJob(jobId);
    return job.documentId || null;
  } catch {
    return null;
  }
}

/**
 * 在 path 上补 `documentId` 查询参数（已有则不覆盖）。
 *
 * @param path 路由 path，可已带 query
 * @param documentId 统一文档 id
 */
export function withDocumentIdQuery(
  path: string,
  documentId?: string | null,
): string {
  if (!documentId) {
    return path;
  }
  const queryIndex = path.indexOf("?");
  const base = queryIndex >= 0 ? path.slice(0, queryIndex) : path;
  const query = queryIndex >= 0 ? path.slice(queryIndex + 1) : "";
  const params = new URLSearchParams(query);
  if (!params.get("documentId")) {
    params.set("documentId", documentId);
  }
  const qs = params.toString();
  return qs ? `${base}?${qs}` : base;
}

export function getHotNewsArticle(
  articleId: string,
  documentId?: string | null,
) {
  return fetch(hotArticlesOrDocumentsPath(articleId, documentId, ""), {
    cache: "no-store",
  }).then((r) => read<HotNewsArticle>(r));
}

export function saveHotNewsArticle(
  articleId: string,
  body: {
    content: string;
    baseContentHash: string;
    reason?: string;
    /** 公众号轨 documentId；有则走 PUT /api/documents/{id} */
    documentId?: string | null;
  },
) {
  const { documentId, ...rest } = body;
  return fetch(hotArticlesOrDocumentsPath(articleId, documentId, ""), {
    method: "PUT",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(rest),
  }).then((r) => read<HotNewsArticle>(r));
}

export function saveHotNewsArticleContent(
  articleId: string,
  contentType: HotNewsDocumentKind,
  body: {
    content: string;
    baseContentHash?: string | null;
    reason?: string;
    /** 对应轨 documentId；缺省时按 contentType 派生 */
    documentId?: string | null;
  },
) {
  const { documentId, ...rest } = body;
  const resolved = resolveHotNewsDocumentId(articleId, documentId, contentType);
  return fetch(hotArticlesOrDocumentsPath(articleId, resolved, "", contentType), {
    method: "PUT",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(rest),
  }).then((r) => read<HotNewsArticle>(r));
}

export function updateHotNewsGenerationSettings(
  articleId: string,
  body: {
    wordCountOption: string;
    imageCountOption: string;
    documentId?: string | null;
  },
) {
  const { documentId, ...rest } = body;
  const resolved = resolveHotNewsDocumentId(articleId, documentId);
  return fetch(
    hotArticlesOrDocumentsPath(articleId, resolved, "/generation-settings"),
    {
      method: "PATCH",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        ...rest,
        documentId: resolved,
      }),
    },
  ).then((r) => read<HotNewsArticle>(r));
}

export function startHotNewsArticleGeneration(
  articleId: string,
  body: {
    scope: "full" | "draft" | "wechat" | "images" | "audit";
    wordCountOption: string;
    imageCountOption: string;
    documentId?: string | null;
  },
) {
  const { documentId, ...rest } = body;
  const resolved = resolveHotNewsDocumentId(articleId, documentId);
  return fetch(
    hotArticlesOrDocumentsPath(articleId, resolved, "/generation-runs"),
    {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        ...rest,
        documentId: resolved,
      }),
    },
  ).then((r) => read<JobData>(r));
}

export function listHotNewsArticleGenerationRuns(
  articleId: string,
  documentId?: string | null,
) {
  return fetch(
    hotArticlesOrDocumentsPath(articleId, documentId, "/generation-runs"),
    { cache: "no-store" },
  ).then((r) => read<JobData[]>(r));
}

export function listHotNewsArticleVersions(
  articleId: string,
  documentId?: string | null,
) {
  return fetch(
    hotArticlesOrDocumentsPath(articleId, documentId, "/versions"),
    { cache: "no-store" },
  ).then((r) => read<HotNewsArticleVersion[]>(r));
}
export function listHotNewsArticleImages(
  articleId: string,
  documentId?: string | null,
) {
  return fetch(
    hotArticlesOrDocumentsPath(articleId, documentId, "/images"),
    { cache: "no-store" },
  ).then((r) => read<HotNewsGeneratedImage[]>(r));
}
export async function runHotNewsAudit(
  articleId: string,
  documentId?: string | null,
) {
  const resolved = resolveHotNewsDocumentId(articleId, documentId);
  const job = await fetch(`/api/hot-article-reviews`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({
      articleId,
      mode: "full",
      documentId: resolved,
    }),
  }).then((r) => read<JobData>(r));
  await waitForBackgroundJob(job.id);
  return getHotNewsAudit(articleId, resolved);
}
export function getHotNewsAudit(articleId: string, documentId?: string | null) {
  const resolved = resolveHotNewsDocumentId(articleId, documentId);
  return fetch(
    `/api/documents/${encodeURIComponent(resolved)}/hot-reviews/latest`,
    { cache: "no-store" },
  ).then((r) => read<HotNewsAudit>(r));
}

export async function repairHotArticleReview(reviewId: string, issueIds: string[]) {
  const job = await fetch(`/api/hot-article-reviews/${encodeURIComponent(reviewId)}/repair`, {
    method: "POST", headers: { "Content-Type": "application/json" },
    body: JSON.stringify(issueIds),
  }).then((r) => read<JobData>(r));
  const completed = await waitForBackgroundJob(job.id);
  return (completed.output || {}) as Record<string, unknown>;
}

export async function repairAllHotArticleReview(reviewId: string) {
  const job = await fetch(`/api/hot-article-reviews/${encodeURIComponent(reviewId)}/repair-all`, {
    method: "POST",
  }).then((r) => read<JobData>(r));
  const completed = await waitForBackgroundJob(job.id);
  return (completed.output || {}) as Record<string, unknown>;
}

export function acceptHotArticleRepair(candidateId: string) {
  return fetch(`/api/hot-article-repairs/${encodeURIComponent(candidateId)}/accept`, {
    method: "POST",
  }).then((r) => read<JobData>(r)).then((job) => waitForBackgroundJob(job.id));
}

export function rejectHotArticleRepair(candidateId: string) {
  return fetch(`/api/hot-article-repairs/${encodeURIComponent(candidateId)}/reject`, {
    method: "POST",
  }).then((r) => read<Record<string, unknown>>(r));
}
export function submitHotArticleReviewDecision(reviewId: string, status: "approved" | "rejected", comment: string) {
  return fetch(`/api/hot-article-reviews/${encodeURIComponent(reviewId)}/decision`, {
    method: "POST", headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ status, comment }),
  }).then((r) => read<Record<string, unknown>>(r));
}
export function submitHotNewsHumanReview(
  articleId: string,
  status: "approved" | "rejected",
  comment: string,
  documentId?: string | null,
) {
  const resolved = resolveHotNewsDocumentId(articleId, documentId);
  return fetch(
    hotArticlesOrDocumentsPath(articleId, resolved, "/human-review"),
    {
      method: "PUT",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        status,
        comment,
        documentId: resolved,
      }),
    },
  ).then((r) => read<HotNewsHumanReview>(r));
}
export function getHotNewsHumanReview(
  articleId: string,
  documentId?: string | null,
) {
  return fetch(
    hotArticlesOrDocumentsPath(articleId, documentId, "/human-review"),
    { cache: "no-store" },
  ).then((r) => read<HotNewsHumanReview>(r));
}
export function createHotNewsPublishingSnapshot(
  articleId: string,
  themeVersionId?: string,
  documentId?: string | null,
) {
  const resolved = resolveHotNewsDocumentId(articleId, documentId);
  return fetch(
    hotArticlesOrDocumentsPath(articleId, resolved, "/publishing-snapshots"),
    {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        themeVersionId: themeVersionId || null,
        documentId: resolved,
      }),
    },
  ).then((r) => read<HotNewsPublishingSnapshot>(r));
}
export function listHotNewsPublishingSnapshots(
  articleId: string,
  documentId?: string | null,
) {
  return fetch(
    hotArticlesOrDocumentsPath(articleId, documentId, "/publishing-snapshots"),
    { cache: "no-store" },
  ).then((r) => read<HotNewsPublishingSnapshot[]>(r));
}
export type HotNewsWorkflowNode = WorkflowNodeData;

/**
 * 工作区曾按 workflowRunId 拉节点；对应接口不存在且调用方吞错为空。
 * 直接返回空数组，界面表现不变。
 */
export function listHotNewsWorkflowNodes(_workflowRunId: string) {
  return Promise.resolve([] as HotNewsWorkflowNode[]);
}
