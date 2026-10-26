package com.chengwenfang.crawler.facade.enums;

/**
 * Tavily 搜索主题。
 */
public enum TavilyTopic {
  /** 新闻向。 */
  NEWS("news"),
  /** 通用检索。 */
  GENERAL("general");

  private final String value;

  TavilyTopic(String value) {
    this.value = value;
  }

  /** @return API topic 参数 */
  public String getValue() {
    return value;
  }

  /**
   * @param raw 原始字符串
   * @return 枚举；无法识别时返回 {@link #NEWS}
   */
  public static TavilyTopic fromOrDefault(String raw) {
    if (raw == null || raw.isBlank()) {
      return NEWS;
    }
    String normalized = raw.trim().toLowerCase();
    for (TavilyTopic item : values()) {
      if (item.value.equals(normalized)) {
        return item;
      }
    }
    return NEWS;
  }
}
