package com.chengwenfang.job.facade.constant;

import java.util.Set;

/** 系统登记的异步任务类型常量及全集。 */
public final class JobTypes {
  public static final String HOT_NEWS_FETCH = "hot_news_fetch";
  public static final String COURSE_CONTENT_GENERATION = "course_content_generation";
  public static final String HOT_NEWS_ARTICLE_GENERATION = "hot_news_article_generation";
  public static final String HOT_NEWS_ARTICLE_REWRITE = "hot_news_article_rewrite";
  public static final String HOT_NEWS_TOPIC_RESEARCH = "hot_news_topic_research";
  /** 选题资料池视频解析（批量/单条）。 */
  public static final String HOT_NEWS_VIDEO_PARSE = "hot_news_video_parse";
  /** 雷达信源视频自动解析（事实速览 + 营销弃用）。 */
  public static final String HOT_NEWS_SOURCE_VIDEO_PARSE = "hot_news_source_video_parse";
  public static final String HOT_ARTICLE_REVIEW = "hot_article_review";
  public static final String HOT_ARTICLE_REVIEW_REPAIR = "hot_article_review_repair";
  public static final String HOT_ARTICLE_REVIEW_REVALIDATE = "hot_article_review_revalidate";
  public static final String COURSE_DRAFT_GENERATION = "course_draft_generation";
  public static final String WECHAT_ARTICLE_GENERATION = "wechat_article_generation";
  public static final String IMAGE_GENERATION = "image_generation";
  public static final String ARTICLE_REPAIR = "article_repair";
  public static final String THEME_CURRICULUM_GENERATION = "theme_curriculum_generation";
  /** 单条知识库文档索引。 */
  public static final String KNOWLEDGE_INDEX_DOCUMENT = "knowledge_index_document";
  /** 知识库全量/按来源重建索引。 */
  public static final String KNOWLEDGE_REINDEX_ALL = "knowledge_reindex_all";
  /** 知识库 MySQL/向量/图对账。 */
  public static final String KNOWLEDGE_RECONCILE = "knowledge_reconcile";

  public static final Set<String> ALL = Set.of(
      HOT_NEWS_FETCH,
      COURSE_CONTENT_GENERATION,
      HOT_NEWS_ARTICLE_GENERATION,
      HOT_NEWS_ARTICLE_REWRITE,
      HOT_NEWS_TOPIC_RESEARCH,
      HOT_NEWS_VIDEO_PARSE,
      HOT_NEWS_SOURCE_VIDEO_PARSE,
      HOT_ARTICLE_REVIEW,
      HOT_ARTICLE_REVIEW_REPAIR,
      HOT_ARTICLE_REVIEW_REVALIDATE,
      COURSE_DRAFT_GENERATION,
      WECHAT_ARTICLE_GENERATION,
      IMAGE_GENERATION,
      ARTICLE_REPAIR,
      THEME_CURRICULUM_GENERATION,
      KNOWLEDGE_INDEX_DOCUMENT,
      KNOWLEDGE_REINDEX_ALL,
      KNOWLEDGE_RECONCILE
  );

  /**
   * 终态不推站内通知的任务类型。
   * <p>单文档索引常批量触发，成功/失败均不打扰；全量重建仍通知。
   */
  public static final Set<String> SILENT_TERMINAL = Set.of(KNOWLEDGE_INDEX_DOCUMENT);

  private JobTypes() {}
}
