package com.chengwenfang.mode.hotnews.facade.constant;

/**
 * 热点选题来源形态（开集；写入 score_details.originKind）。
 */
public final class HotNewsTopicOriginKinds {
  /** 按主题问卷直接生成的 AI 主题种子素材。 */
  public static final String AI_THEME_SEED = "ai_theme_seed";

  /** 展示标签：AI 主题素材。 */
  public static final String AI_THEME_SEED_LABEL = "AI 主题素材";

  /** AI 联网自搜产出的有出处选题素材。 */
  public static final String AI_WEB_SEARCH = "ai_web_search";

  /** 展示标签：AI 联网选题。 */
  public static final String AI_WEB_SEARCH_LABEL = "AI 联网选题";

  /**
   * 主题下「AI 联网选题」占位渠道 baseUrl（disabled，不参与 FETCH_CHANNELS）。
   * 用于 source_items.channel_id 外键。
   */
  public static final String AI_WEB_SEARCH_CHANNEL_BASE_URL =
      "https://local.chengwenfang/ai-web-search";

  /** 用户手动添加的选题。 */
  public static final String MANUAL = "manual";

  /** 展示标签：手动添加。 */
  public static final String MANUAL_LABEL = "手动添加";

  /**
   * 主题下「手动添加」占位渠道 baseUrl（disabled，不参与 FETCH_CHANNELS）。
   * 用于手动选题附带链接时 source_items.channel_id 外键。
   */
  public static final String MANUAL_CHANNEL_BASE_URL =
      "https://local.chengwenfang/manual-topic";

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

  /**
   * 是否为 AI 联网自搜素材。
   *
   * @param originKind 来源形态
   * @return 匹配则为 true
   */
  public static boolean isAiWebSearch(String originKind) {
    return AI_WEB_SEARCH.equals(originKind);
  }

  /**
   * 是否为用户手动添加选题。
   *
   * @param originKind 来源形态
   * @return 匹配则为 true
   */
  public static boolean isManual(String originKind) {
    return MANUAL.equals(originKind);
  }
}
