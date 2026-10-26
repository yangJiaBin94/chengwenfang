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
      THEME_CURRICULUM_GENERATION
  );

  private JobTypes() {}
}
