package com.workbench.mode.hotnews.application.generation.hotnews;

/**
 * 热点图状态在 GraphRuntime Map 边界上的字段名（与 {@link HotNewsGraphState} 互转）。
 */
public final class HotNewsGraphStateKeys {
  private HotNewsGraphStateKeys() {}

  /** 文章 ID。 */
  public static final String ARTICLE_ID = "articleId";
  /** 选题 ID。 */
  public static final String TOPIC_ID = "topicId";
  /** 简报 ID。 */
  public static final String BRIEF_ID = "briefId";
  /** Job ID。 */
  public static final String JOB_ID = "jobId";
  /** 标题。 */
  public static final String TITLE = "title";
  /** 选题摘要。 */
  public static final String TOPIC_SUMMARY = "topicSummary";
  /** 主题成文上下文（读者/边界等）。 */
  public static final String THEME_CONTEXT = "themeContext";
  /** 骨架对象。 */
  public static final String SKELETON = "skeleton";
  /** 骨架 JSON。 */
  public static final String SKELETON_JSON = "skeletonJson";
  /** 参考资料 JSON。 */
  public static final String REFERENCES_JSON = "referencesJson";
  /** Skill 快照摘要。 */
  public static final String SKILL_SNAPSHOT_SUMMARY = "skillSnapshotSummary";
  /** 事实包 JSON。 */
  public static final String FACTS_JSON = "factsJson";
  /** 文章定位说明。 */
  public static final String POSITIONING = "positioning";
  /** 原稿结构规划。 */
  public static final String DRAFT_PLAN = "draftPlan";
  /** 配图规划。 */
  public static final String IMAGE_PLAN = "imagePlan";
  /** 字数档。 */
  public static final String WORD_COUNT_OPTION = "wordCountOption";
  /** 插图档。 */
  public static final String IMAGE_COUNT_OPTION = "imageCountOption";
  /** 原稿。 */
  public static final String DRAFT = "draft";
  /** 公众号正文。 */
  public static final String WECHAT = "wechat";
  /** 原稿审校通过标记。 */
  public static final String REVIEW_PASSED = "reviewPassed";
  /** 公众号审校通过标记。 */
  public static final String WECHAT_REVIEW_PASSED = "wechatReviewPassed";
  /** 跳过配图标记。 */
  public static final String IMAGES_SKIPPED = "imagesSkipped";
  /** 配图校验通过标记。 */
  public static final String IMAGES_VALIDATED = "imagesValidated";
  /** 最终完整性检查通过标记。 */
  public static final String FINAL_VALIDATION_PASSED = "finalValidationPassed";
  /** 已生成配图列表。 */
  public static final String GENERATED_IMAGES = "generatedImages";
  /** 提交后的文章视图。 */
  public static final String ARTICLE = "article";
  /** 已提交标记。 */
  public static final String COMMITTED = "committed";
  /** 当前热点步骤 typeKey。 */
  public static final String HOT_NEWS_STEP = "hotNewsStep";
}
