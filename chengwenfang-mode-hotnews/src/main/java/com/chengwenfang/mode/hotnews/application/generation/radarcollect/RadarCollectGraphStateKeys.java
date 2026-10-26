package com.chengwenfang.mode.hotnews.application.generation.radarcollect;

/**
 * 雷达采集图状态键（写入 GraphRuntime Map 的轻量字段）。
 */
public final class RadarCollectGraphStateKeys {
  private RadarCollectGraphStateKeys() {}

  /** 主题 ID。 */
  public static final String THEME_ID = "themeId";
  /** 采集 run ID（source_fetch_runs）。 */
  public static final String FETCH_RUN_ID = "fetchRunId";
  /** Job ID。 */
  public static final String JOB_ID = "jobId";
  /** 回溯小时。 */
  public static final String HOURS = "hours";
  /** 渠道 ID 列表。 */
  public static final String CHANNEL_IDS = "channelIds";
  /** 本轮批次 ID。 */
  public static final String RADAR_BATCH_ID = "radarBatchId";
  /** 文章素材配额。 */
  public static final String MAX_ARTICLE_PER_FETCH = "maxArticlePerFetch";
  /** 视频素材配额。 */
  public static final String MAX_VIDEO_PER_FETCH = "maxVideoPerFetch";
  /** Skill 快照摘要。 */
  public static final String SKILL_SNAPSHOT_SUMMARY = "skillSnapshotSummary";
  /** 候选总数。 */
  public static final String CANDIDATE_COUNT = "candidateCount";
  /** 幸存者数。 */
  public static final String SURVIVOR_COUNT = "survivorCount";
  /** 解析步骤可读摘要（供执行页小字展示）。 */
  public static final String ENRICH_NOTE = "enrichNote";
  /** 获取渠道步骤可读摘要。 */
  public static final String FETCH_NOTE = "fetchNote";
  /** 素材筛选步骤可读摘要。 */
  public static final String FILTER_NOTE = "filterNote";
  /** 落库成簇步骤可读摘要。 */
  public static final String PERSIST_NOTE = "persistNote";
  /** 解析步骤弃用数。 */
  public static final String ENRICH_DISCARDED_COUNT = "enrichDiscardedCount";
  /** 待解析文章数。 */
  public static final String ENRICH_ARTICLE_COUNT = "enrichArticleCount";
  /** 待解析视频数。 */
  public static final String ENRICH_VIDEO_COUNT = "enrichVideoCount";
  /** 文章发布日统计摘要（各来源计数）。 */
  public static final String ENRICH_PUBLISH_DATE_NOTE = "enrichPublishDateNote";
  /** 无可用正文、未尝试抽日期的文章 URL 列表。 */
  public static final String ENRICH_NO_CONTENT_URLS = "enrichNoContentUrls";
  /** 有正文但各路均未抽到日期的文章 URL 列表。 */
  public static final String ENRICH_NO_DATE_URLS = "enrichNoDateUrls";
  /** 获取原始条数（过滤前）。 */
  public static final String FETCH_RAW_COUNT = "fetchRawCount";
  /** 获取阶段过滤掉的条数。 */
  public static final String FETCH_FILTERED_COUNT = "fetchFilteredCount";
  /** 素材筛选参与条数。 */
  public static final String FILTER_INPUT_COUNT = "filterInputCount";
  /** 素材筛选弃用条数。 */
  public static final String FILTER_DISCARDED_COUNT = "filterDiscardedCount";
  /** 落库成簇参与条数。 */
  public static final String PERSIST_INPUT_COUNT = "persistInputCount";
  /** 落库新增数。 */
  public static final String INSERTED_COUNT = "insertedCount";
  /** 选题簇数。 */
  public static final String TOPIC_COUNT = "topicCount";
  /** Top10 数。 */
  public static final String TOP10_COUNT = "top10Count";
  /** AI 主题种子步骤可读摘要。 */
  public static final String AI_THEME_SEED_NOTE = "aiThemeSeedNote";
  /** AI 主题种子选题 ID（本批）。 */
  public static final String AI_THEME_SEED_TOPIC_ID = "aiThemeSeedTopicId";
  /** 评分取 Top10 步骤可读摘要。 */
  public static final String SCORE_TOP10_NOTE = "scoreTop10Note";
  /** 当前步骤 typeKey。 */
  public static final String RADAR_STEP = "radarStep";
  /**
   * 租约续期回调（{@link Runnable}），由管道注入；图状态序列化时忽略。
   */
  public static final String LEASE_RENEWAL = "leaseRenewal";
}
