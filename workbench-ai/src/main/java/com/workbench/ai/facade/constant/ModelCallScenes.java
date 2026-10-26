package com.workbench.ai.facade.constant;

/**
 * 已知 {@code model_calls.scene} 常量（业务侧还可传入自定义 scene；动态拼接如 {@code wechat_chunk_N} 除外）。
 */
public final class ModelCallScenes {
  /** 连通性探测。 */
  public static final String CONNECTION_TEST = "connection_test";
  /** 图像生成。 */
  public static final String IMAGE = "image";
  /** 临时/调试补全。 */
  public static final String ADHOC = "adhoc";

  /** 主题课表草案生成。 */
  public static final String THEME_CURRICULUM_GENERATION = "theme_curriculum_generation";
  /** 课程分段原稿生成/审核落稿。 */
  public static final String COURSE_DRAFT = "course_draft";
  /** 课程原稿超限压缩。 */
  public static final String DRAFT_COMPRESS = "draft_compress";
  /** 课程文章符号修复。 */
  public static final String ARTICLE_REPAIR = "article_repair";

  /** 雷达采集：文章事实速览。 */
  public static final String RADAR_ARTICLE_FACT_BRIEF = "radar_article_fact_brief";
  /** 雷达采集：文章发布日抽取。 */
  public static final String RADAR_ARTICLE_PUBLISH_DATE = "radar_article_publish_date";
  /** 雷达采集：视频事实速览。 */
  public static final String RADAR_VIDEO_FACT_BRIEF = "radar_video_fact_brief";
  /** 雷达采集：营销过滤。 */
  public static final String RADAR_CANDIDATE_FILTER_MARKETING = "radar_candidate_filter_marketing";
  /** 雷达采集：词典未命中标题的主题相关性整包判定。 */
  public static final String RADAR_CANDIDATE_FILTER_THEME = "radar_candidate_filter_theme";
  /** 雷达采集：簇摘要合并。 */
  public static final String RADAR_CLUSTER_SUMMARY_MERGE = "radar_cluster_summary_merge";
  /** 雷达采集：按主题问卷生成 AI 主题种子素材。 */
  public static final String RADAR_AI_THEME_SEED = "radar_ai_theme_seed";

  /** 热点主题：生成可检索短搜索词。 */
  public static final String HOT_NEWS_THEME_SEARCH_QUERIES = "hot_news_theme_search_queries";
  /** 热点主题：生成写作价值评分量表。 */
  public static final String HOT_NEWS_THEME_SCORING_RUBRIC = "hot_news_theme_scoring_rubric";
  /** 热点主题：生成 GitHub Search 查询串。 */
  public static final String HOT_NEWS_THEME_GITHUB_QUERY = "hot_news_theme_github_query";
  /** 热点主题：AI 生成来源渠道候选。 */
  public static final String HOT_NEWS_THEME_SOURCE_CHANNELS = "hot_news_theme_source_channels";

  /** 热点选题：按主题量表 AI 写作价值打分。 */
  public static final String HOT_NEWS_TOPIC_WRITING_SCORE = "hot_news_topic_writing_score";
  /** 热点选题：英文标题/速览中文提炼。 */
  public static final String HOT_NEWS_TOPIC_TRANSLATION = "hot_news_topic_translation";
  /** 选题资料池：检索关键词提取。 */
  public static final String HOT_NEWS_TOPIC_RESEARCH_KEYWORDS = "hot_news_topic_research_keywords";
  /** 选题资料池：相关性过滤。 */
  public static final String HOT_NEWS_TOPIC_RESEARCH_RELEVANCE = "hot_news_topic_research_relevance";
  /** 选题资料池：非中文标题/摘要翻译。 */
  public static final String HOT_NEWS_TOPIC_RESEARCH_TRANSLATE = "hot_news_topic_research_translate";
  /** 选题资料池：素材五维综合分打分。 */
  public static final String HOT_NEWS_RESEARCH_ITEM_SCORE = "hot_news_research_item_score";

  /** 视频转写：口播摘要。 */
  public static final String HOT_NEWS_VIDEO_PARSE_SUMMARIZE = "hot_news_video_parse_summarize";
  /** 视频转写：关键帧视觉摘要。 */
  public static final String HOT_NEWS_VIDEO_PARSE_VISION_SUMMARIZE =
      "hot_news_video_parse_vision_summarize";
  /** 信源视频：事实速览。 */
  public static final String HOT_NEWS_SOURCE_VIDEO_FACT_BRIEF = "hot_news_source_video_fact_brief";
  /** 信源视频：营销意图判定。 */
  public static final String HOT_NEWS_SOURCE_VIDEO_PROMO_JUDGE = "hot_news_source_video_promo_judge";
  /** 选题：文章骨架拟定。 */
  public static final String HOT_NEWS_ARTICLE_SKELETON = "hot_news_article_skeleton";
  /** 热点文章改写。 */
  public static final String HOT_NEWS_REWRITE = "hot_news_rewrite";
  /** 热点审稿修复。 */
  public static final String HOT_NEWS_REVIEW_REPAIR = "hot_news_review_repair";

  private ModelCallScenes() {}
}
