package com.workbench.sourcing.facade.constant;

/**
 * 热点信源渠道配额上限。
 */
public final class HotNewsChannelLimits {
  /** 每个主题最多来源渠道数。 */
  public static final int MAX_CHANNELS_PER_THEME = 10;
  /** 单条网页渠道最多 URL 数。 */
  public static final int MAX_WEB_URLS_PER_CHANNEL = 5;

  private HotNewsChannelLimits() {}
}
