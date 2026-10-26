package com.workbench.mode.hotnews.facade.constant;

/**
 * 热点业务错误码常量。
 */
public final class HotNewsErrorCodes {
  private HotNewsErrorCodes() {}

  /** 缺少 articleId。 */
  public static final String ARTICLE_ID_REQUIRED = "article_id_required";
  /** 缺少文章骨架。 */
  public static final String ARTICLE_SKELETON_REQUIRED = "article_skeleton_required";
  /** Skill 快照缺失或不可用。 */
  public static final String SKILL_SNAPSHOT_REQUIRED = "hot_news_skill_snapshot_required";
  /** 事实包缺失。 */
  public static final String FACTS_REQUIRED = "hot_news_facts_required";
  /** 公众号审校未通过。 */
  public static final String WECHAT_REVIEW_FAILED = "hot_news_wechat_review_failed";
  /** 配图校验未通过。 */
  public static final String IMAGE_VALIDATION_FAILED = "hot_news_image_validation_failed";
  /** 最终完整性检查未通过。 */
  public static final String FINAL_VALIDATION_FAILED = "hot_news_final_validation_failed";
  /** 字数档校验未通过。 */
  public static final String WORD_COUNT_FAILED = "hot_news_word_count_failed";
  /** 文章骨架缺少结构规划。 */
  public static final String SKELETON_STRUCTURE_REQUIRED = "hot_news_skeleton_structure_required";
  /** 提交版本时草稿或公众号正文不完整。 */
  public static final String COMMIT_INCOMPLETE = "hot_news_commit_incomplete";
}
