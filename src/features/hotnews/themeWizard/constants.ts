/** 领域选项。 */
export const DOMAIN_OPTIONS = [
  { value: "tech_product", label: "科技产品" },
  { value: "policy", label: "行业政策" },
  { value: "funding", label: "创投融资" },
  { value: "opensource", label: "开源生态" },
  { value: "consumer_brand", label: "消费品牌" },
  { value: "custom", label: "自定义" },
] as const;

/** 初始搜索渠道选项。 */
export const SEED_CHANNEL_OPTIONS = [
  { value: "searxng", label: "SearXNG（推荐）" },
  { value: "tavily", label: "Tavily" },
  { value: "github", label: "GitHub" },
] as const;

/** 评分维度数量下限。 */
export const MIN_RUBRIC_DIMS = 3;
/** 评分维度数量上限。 */
export const MAX_RUBRIC_DIMS = 6;
/** 单条搜索词最大字符数；与后端 SearchQueryNormalizer 对齐。 */
export const MAX_SEARCH_QUERY_CHARS = 64;
/** 单次配置最多保留的搜索词条数。 */
export const MAX_SEARCH_QUERIES = 12;
