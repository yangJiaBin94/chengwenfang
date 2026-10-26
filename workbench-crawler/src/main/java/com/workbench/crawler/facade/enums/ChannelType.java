package com.workbench.crawler.facade.enums;

/**
 * 来源渠道抓取类型（闭集）。
 * <p>每种类型对应一个 {@code ChannelFetchStrategy} 实现。
 */
public enum ChannelType {
  /** RSS / Atom 订阅源。 */
  RSS("rss"),
  /** 单页网页。 */
  WEB("web"),
  /** GitHub 仓库搜索。 */
  GITHUB("github"),
  /** SearXNG 元搜索。 */
  SEARXNG("searxng"),
  /** Tavily 新闻搜索 API。 */
  TAVILY("tavily");

  /** 持久化与 API 使用的小写字面量。 */
  private final String value;

  ChannelType(String value) {
    this.value = value;
  }

  /**
   * @return 对外字面量（如 {@code rss}）
   */
  public String getValue() {
    return value;
  }

  /**
   * 将字符串解析为枚举；无法识别时返回 {@code null}。
   *
   * @param raw 类型字符串，可空、可大小写混用
   * @return 匹配的类型；空白或未知返回 null
   */
  public static ChannelType from(String raw) {
    if (raw == null || raw.isBlank()) {
      return null;
    }
    String normalized = raw.trim().toLowerCase();
    for (ChannelType type : values()) {
      if (type.value.equals(normalized)) {
        return type;
      }
    }
    return null;
  }
}
