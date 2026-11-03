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

/** 采集策略选项。 */
export const COLLECT_STRATEGY_OPTIONS = [
  {
    value: "structured",
    label: "结构化",
    hint: "固定搜索词 + RSS/搜索渠道即可稳定拿到素材",
  },
  {
    value: "unstructured",
    label: "非结构化",
    hint: "按意图多轮规划搜索（如先发现火热板块再搜个股）",
  },
] as const;

/** 向导总步数（含意图确认）。 */
export const WIZARD_STEP_COUNT = 5;
