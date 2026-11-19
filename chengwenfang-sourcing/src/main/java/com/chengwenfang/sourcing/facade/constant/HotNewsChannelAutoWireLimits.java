package com.chengwenfang.sourcing.facade.constant;

/**
 * 热点主题自动配源配额。
 */
public final class HotNewsChannelAutoWireLimits {
  private HotNewsChannelAutoWireLimits() {}

  /** 单次从沉淀池最多克隆的渠道数（不含固定 SearXNG）。 */
  public static final int MAX_CATALOG_MATCHES = 8;
}
