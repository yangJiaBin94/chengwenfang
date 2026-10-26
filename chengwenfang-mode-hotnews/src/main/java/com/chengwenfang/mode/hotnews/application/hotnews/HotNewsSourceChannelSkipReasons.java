package com.chengwenfang.mode.hotnews.application.hotnews;

/**
 * AI 生成来源渠道时的跳过原因文案（集中常量，避免散落魔法串）。
 */
public final class HotNewsSourceChannelSkipReasons {
  private HotNewsSourceChannelSkipReasons() {}

  /** 类型无法识别。 */
  public static final String INVALID_TYPE = "渠道类型无效";
  /** 名称或地址缺失。 */
  public static final String INVALID_FIELDS = "名称或地址无效";
  /** 与已有渠道重复。 */
  public static final String DUPLICATE = "与已有来源重复";
  /** 主题已有 SearXNG，不再新增。 */
  public static final String SEARXNG_EXISTS = "主题已有 SearXNG 搜索渠道";
  /** 连通性探测失败。 */
  public static final String CONNECTIVITY_FAILED = "连通性校验未通过";
  /** 网页多地址全部不可用。 */
  public static final String NO_VALID_URL = "没有可用的网页地址";
  /** 搜索类缺少查询词。 */
  public static final String MISSING_QUERIES = "缺少搜索查询词";
  /** GitHub 缺少 query。 */
  public static final String MISSING_GITHUB_QUERY = "缺少 GitHub 查询串";
  /** 本轮已新建网页渠道，后续网页候选跳过。 */
  public static final String WEB_ALREADY_CREATED = "本轮已创建网页来源，多余网页候选已跳过";
  /** 主题渠道数已达上限。 */
  public static final String THEME_CHANNEL_LIMIT = "主题来源渠道数已达上限";
}
