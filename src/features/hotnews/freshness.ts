/**
 * 话题素材时效窗口（与后端 HotNewsTopicFreshness 对齐，写死）。
 * 雷达抓取默认 hours 与「追求时效性」硬闸共用。
 */
export const HOT_NEWS_TOPIC_FRESHNESS_WINDOW_DAYS = 7;

/** 回溯小时数 = 天数 × 24。 */
export const HOT_NEWS_TOPIC_FRESHNESS_WINDOW_HOURS =
  HOT_NEWS_TOPIC_FRESHNESS_WINDOW_DAYS * 24;
