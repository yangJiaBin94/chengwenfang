package com.workbench.sourcing.facade.constant;

/**
 * 热点抓取 Job 入参与进度/出参字段名。
 */
public final class HotNewsFetchKeys {
  private HotNewsFetchKeys() {}

  /** 入参：主题 ID。 */
  public static final String THEME_ID = "themeId";
  /**
   * 入参缺省主题（须已存在于 content_themes；与内容域种子主题同 id，满足 theme_id FK）。
   */
  public static final String DEFAULT_THEME_ID = "theme-ai-self-study-zero";
  /** 入参：渠道 ID 列表。 */
  public static final String CHANNEL_IDS = "channelIds";
  /** 入参：回溯小时。 */
  public static final String HOURS = "hours";
  /** 入参 / 出参：抓取运行 ID。 */
  public static final String FETCH_RUN_ID = "fetchRunId";
  /** 运行视图：人工触发。 */
  public static final String TRIGGER_MANUAL = "manual";
  /** 进度阶段：采集中。 */
  public static final String STAGE_FETCH = "fetch";
  /** 进度阶段：选题聚合。 */
  public static final String STAGE_TOPIC_AGGREGATE = "topic_aggregate";
  /** 进度阶段：中文速览。 */
  public static final String STAGE_TOPIC_TRANSLATE = "topic_translate";
  /** 进度阶段：完成。 */
  public static final String STAGE_DONE = "done";
  /** 事件：单渠道失败。 */
  public static final String EVENT_CHANNEL_FAILED = "channel_failed";
  /** 事件：中文速览失败。 */
  public static final String EVENT_TOPIC_TRANSLATE_FAILED = "topic_translate_failed";
  /** 出参：总条数。 */
  public static final String TOTAL_ITEMS = "totalItems";
  /** 出参：新增条数。 */
  public static final String NEW_ITEMS = "newItems";
  /** 出参：去重条数。 */
  public static final String DEDUPED_ITEMS = "dedupedItems";
  /** 出参：选题数。 */
  public static final String TOPIC_COUNT = "topicCount";
  /** 出参：已翻译选题数。 */
  public static final String TRANSLATED_TOPICS = "translatedTopics";
  /** 渠道运行记录：channelId。 */
  public static final String CHANNEL_ID = "channelId";
  /** 渠道运行记录：errorCode。 */
  public static final String ERROR_CODE = "errorCode";
}
