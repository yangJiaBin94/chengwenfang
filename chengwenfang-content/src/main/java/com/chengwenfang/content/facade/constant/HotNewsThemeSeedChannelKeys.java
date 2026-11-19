package com.chengwenfang.content.facade.constant;

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

  /** 启用时创建的 SearXNG 渠道固定展示名。 */
  public static final String SEARXNG_DISPLAY_NAME = "SearXNG 搜索";
  /** 启用时创建的 Tavily 渠道固定展示名。 */
  public static final String TAVILY_DISPLAY_NAME = "Tavily 搜索";
  /** 启用时创建的 GitHub 渠道固定展示名。 */
  public static final String GITHUB_DISPLAY_NAME = "GitHub 搜索";
}
