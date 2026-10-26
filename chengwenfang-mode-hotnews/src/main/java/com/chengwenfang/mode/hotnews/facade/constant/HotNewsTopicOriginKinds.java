package com.chengwenfang.mode.hotnews.facade.constant;

/**
 * 热点选题来源形态（开集；写入 score_details.originKind）。
 */
public final class HotNewsTopicOriginKinds {
  /** 按主题问卷直接生成的 AI 主题种子素材。 */
  public static final String AI_THEME_SEED = "ai_theme_seed";

  /** 展示标签：AI 主题素材。 */
  public static final String AI_THEME_SEED_LABEL = "AI 主题素材";

  private HotNewsTopicOriginKinds() {}

  /**
   * 是否为 AI 主题种子素材。
   *
   * @param originKind 来源形态
   * @return 匹配则为 true
   */
  public static boolean isAiThemeSeed(String originKind) {
    return AI_THEME_SEED.equals(originKind);
  }
}
