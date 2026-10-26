package com.workbench.mode.hotnews.facade.constant;

/**
 * 热点成文可复用文本产物类型（失败重试时按 job + nodeKey + 类型取回）。
 */
public final class HotNewsArtifactTypes {
  private HotNewsArtifactTypes() {}

  /** 事实包 JSON。 */
  public static final String FACTS = "hot_news_facts";
  /** 生成后的原稿（审校前）。 */
  public static final String DRAFT = "hot_news_draft";
  /** 审校通过后的原稿。 */
  public static final String REVIEWED_DRAFT = "hot_news_reviewed_draft";
  /** 生成后的公众号正文（审校前）。 */
  public static final String WECHAT = "hot_news_wechat";
  /** 审校通过后的公众号正文。 */
  public static final String REVIEWED_WECHAT = "hot_news_reviewed_wechat";
}
