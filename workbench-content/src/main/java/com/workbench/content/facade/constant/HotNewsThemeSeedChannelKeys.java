package com.workbench.content.facade.constant;

/**
 * 热点主题创建时可选的初始搜索渠道类型键（与 {@code ChannelType} 取值对齐）。
 */
public final class HotNewsThemeSeedChannelKeys {
  private HotNewsThemeSeedChannelKeys() {}

  /** SearXNG 搜索渠道。 */
  public static final String SEARXNG = "searxng";
  /** Tavily 搜索渠道。 */
  public static final String TAVILY = "tavily";
  /** GitHub 搜索渠道。 */
  public static final String GITHUB = "github";
}
