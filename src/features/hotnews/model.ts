/** 热点来源渠道类型字面量（与后端 ChannelType 对齐）。 */
export const ChannelType = {
  RSS: "rss",
  WEB: "web",
  GITHUB: "github",
  SEARXNG: "searxng",
  TAVILY: "tavily",
} as const;

export type ChannelTypeValue = (typeof ChannelType)[keyof typeof ChannelType];

/** 来源类型中文标签。 */
export const CHANNEL_TYPE_LABELS: Record<ChannelTypeValue, string> = {
  [ChannelType.RSS]: "RSS / Atom",
  [ChannelType.WEB]: "网页",
  [ChannelType.GITHUB]: "GitHub",
  [ChannelType.SEARXNG]: "SearXNG 搜索",
  [ChannelType.TAVILY]: "Tavily 搜索",
};

/** 短标签（列表摘要用）。 */
export const CHANNEL_TYPE_SHORT_LABELS: Record<ChannelTypeValue, string> = {
  [ChannelType.RSS]: "RSS",
  [ChannelType.WEB]: "网页",
  [ChannelType.GITHUB]: "GitHub",
  [ChannelType.SEARXNG]: "SearXNG",
  [ChannelType.TAVILY]: "Tavily",
};

/** GitHub 排序。 */
export const GithubSort = {
  STARS: "stars",
  UPDATED: "updated",
  FORKS: "forks",
} as const;

export type GithubSortValue = (typeof GithubSort)[keyof typeof GithubSort];

/** GitHub Search API 默认地址。 */
export const GITHUB_SEARCH_API_URL = "https://api.github.com/search/repositories";

/** Tavily Search API 默认地址。 */
export const TAVILY_SEARCH_API_URL = "https://api.tavily.com/search";

/** 本地 SearXNG 默认实例根地址（对应 compose profile searxng）。 */
export const SEARXNG_DEFAULT_INSTANCE_URL = "http://127.0.0.1:8080";

/** SearXNG 默认单次条数。 */
export const SEARXNG_DEFAULT_MAX_RESULTS = 25;

/** 每个主题最多来源渠道数（与后端 HotNewsChannelLimits 对齐）。 */
export const MAX_CHANNELS_PER_THEME = 10;

/** 单条网页渠道最多 URL 数（与后端 HotNewsChannelLimits 对齐）。 */
export const MAX_WEB_URLS_PER_CHANNEL = 5;

/** Tavily 默认单次条数。 */
export const TAVILY_DEFAULT_MAX_RESULTS = 8;

/** Tavily 默认 topic。 */
export const TAVILY_DEFAULT_TOPIC = "news";

/** Tavily 默认 search_depth。 */
export const TAVILY_DEFAULT_SEARCH_DEPTH = "advanced";

/** 聚合搜索引擎表单元数据（固定名称 / 简介 / 官方链接）。 */
export type SearchEngineFormMeta = {
  fixedName: string;
  description: string;
  docsUrl: string;
  docsLabel: string;
  defaultBaseUrl: string;
  baseUrlEditable: boolean;
  requiresApiKey: boolean;
  defaultMaxResults: number;
  baseUrlHint: string;
  queriesHint: string;
  maxResultsHint: string;
  apiKeyHint?: string;
};

export const SEARCH_ENGINE_FORM_META: Record<
  typeof ChannelType.SEARXNG | typeof ChannelType.TAVILY,
  SearchEngineFormMeta
> = {
  [ChannelType.SEARXNG]: {
    fixedName: "SearXNG 搜索",
    description:
      "开源元搜索引擎，可本地 Docker 部署。工作台按查询词请求其实例 JSON API，再把结果写入热点素材流水线；无需 API Key。",
    docsUrl: "https://docs.searxng.org/",
    docsLabel: "SearXNG 官方文档",
    defaultBaseUrl: SEARXNG_DEFAULT_INSTANCE_URL,
    baseUrlEditable: true,
    requiresApiKey: false,
    defaultMaxResults: SEARXNG_DEFAULT_MAX_RESULTS,
    baseUrlHint:
      "实例根地址（不要带 /search）。本地默认 http://127.0.0.1:8080；需在实例 settings.yml 中启用 formats: [html, json]。",
    queriesHint:
      "按「资讯/事件」写查询词，少写纯厂商名（否则容易搜到官网首页）。换行或英文逗号 = 多次搜索；同行 OR/AND 仍算一次。例：OpenAI 发布 OR ChatGPT 更新。",
    maxResultsHint: "每一条查询词最多保留多少条结果（不是全部查询合计）。",
  },
  [ChannelType.TAVILY]: {
    fixedName: "Tavily 搜索",
    description:
      "面向 Agent 的商业网页搜索 API。按查询词拉取新闻/网页结果并进入热点流水线；需在 Tavily 控制台申请 API Key。",
    docsUrl: "https://docs.tavily.com/",
    docsLabel: "Tavily 官方文档",
    defaultBaseUrl: TAVILY_SEARCH_API_URL,
    baseUrlEditable: false,
    requiresApiKey: true,
    defaultMaxResults: TAVILY_DEFAULT_MAX_RESULTS,
    baseUrlHint: "官方固定 API 地址，无需修改。",
    queriesHint:
      "按「资讯/事件」写查询词，少写纯厂商名。换行或英文逗号 = 多次搜索；同行 OR/AND 仍算一次。默认 topic=news。",
    maxResultsHint: "每一条查询词最多保留多少条结果（不是全部查询合计）。",
    apiKeyHint: "在 Tavily 控制台创建，形如 tvly-…；仅服务端保存，列表不回传明文。",
  },
};

/** 是否为聚合搜索引擎类型。 */
export function isSearchEngineType(
  type: ChannelTypeValue,
): type is typeof ChannelType.SEARXNG | typeof ChannelType.TAVILY {
  return type === ChannelType.SEARXNG || type === ChannelType.TAVILY;
}

/** 渠道扩展配置（与后端 ChannelFetchConfig 对齐）。 */
export type ChannelFetchConfig = {
  query?: string;
  queries?: string[];
  /** 网页渠道多页面地址。 */
  urls?: string[];
  maxResults?: number;
  topic?: string;
  searchDepth?: string;
  apiKey?: string;
  /** GitHub 时间口径；新建固定 updated，旧数据可能仍有 created。 */
  dateField?: "created" | "updated";
  sort?: GithubSortValue;
};

/** 主题量表某一维的 AI 打分。 */
export type TopicDimensionScore = {
  key?: string;
  label?: string;
  weight?: number;
  score?: number | null;
  reason?: string;
};

/** 资料池素材评分明细（与后端 ResearchItemScoreDetails 对齐）。 */
export type ResearchItemScoreDetails = {
  rubricVersion?: number;
  dimensions?: TopicDimensionScore[];
  recommendUse?: string;
  riskNote?: string;
};

/** 选题评分明细（与后端 TopicScoreDetails 对齐）。 */
export type TopicScoreDetails = {
  sourceCount?: number;
  officialCount?: number;
  primaryCount?: number;
  secondaryCount?: number;
  communityCount?: number;
  unknownDateCount?: number;
  sourceTypes?: string[];
  independentSourceCount?: number;
  recentSourceCount?: number;
  aiHits?: number;
  depthHits?: number;
  sourceGrowthRatio?: number;
  freshnessHours?: number;
  signals?: string[];
  metricNote?: string;
  /** AI 分维结果（主题动态量表）。 */
  dimensions?: TopicDimensionScore[];
  rubricVersion?: number;
  rubricSnapshot?: TopicDimensionScore[];
  recommendUse?: string;
  riskNote?: string;
  /** 选题来源形态（如 ai_theme_seed）。 */
  originKind?: string | null;
};

/** AI 主题种子素材来源形态（与后端 HotNewsTopicOriginKinds 对齐）。 */
export const TopicOriginKind = {
  AI_THEME_SEED: "ai_theme_seed",
} as const;

export const TOPIC_ORIGIN_LABELS: Record<string, string> = {
  [TopicOriginKind.AI_THEME_SEED]: "AI 主题素材",
};

/** 是否为 AI 主题种子素材。 */
export function isAiThemeSeedTopic(topic: {
  originKind?: string | null;
  scoreDetails?: TopicScoreDetails | null;
}): boolean {
  const kind = topic.originKind || topic.scoreDetails?.originKind;
  return kind === TopicOriginKind.AI_THEME_SEED;
}

/** 将渠道 type 字面量解析为短标签。 */
export function channelTypeShortLabel(type: string): string {
  if (type === TopicOriginKind.AI_THEME_SEED) {
    return TOPIC_ORIGIN_LABELS[TopicOriginKind.AI_THEME_SEED];
  }
  if (type in CHANNEL_TYPE_SHORT_LABELS) {
    return CHANNEL_TYPE_SHORT_LABELS[type as ChannelTypeValue];
  }
  return type || "公开来源";
}

/**
 * 热点产出 ID 解析：优先使用接口返回的 documentId / draftDocumentId，不再本地 SHA 派生。
 */

/** 热点草稿轨 kind。 */
export const HOT_NEWS_DOCUMENT_KIND_DRAFT = "draft" as const;
/** 热点公众号轨 kind。 */
export const HOT_NEWS_DOCUMENT_KIND_WECHAT = "wechat" as const;

export type HotNewsDocumentKind =
  | typeof HOT_NEWS_DOCUMENT_KIND_DRAFT
  | typeof HOT_NEWS_DOCUMENT_KIND_WECHAT;

/**
 * 解析热点产出 ID：仅接受显式 documentId（接口返回）。
 *
 * @param _articleId 热点文章 id（保留签名兼容，不再用于派生）
 * @param documentId 已知产出 id
 * @param _kind 轨 kind（保留签名兼容）
 * @return 产出 id；缺失时空串
 */
export function resolveHotNewsDocumentId(
  _articleId: string,
  documentId?: string | null,
  _kind: HotNewsDocumentKind = HOT_NEWS_DOCUMENT_KIND_WECHAT,
): string {
  if (documentId && documentId.trim()) {
    return documentId.trim();
  }
  return "";
}
