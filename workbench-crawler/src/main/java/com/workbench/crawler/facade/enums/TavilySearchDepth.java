package com.workbench.crawler.facade.enums;

/**
 * Tavily 搜索深度。
 */
public enum TavilySearchDepth {
  /** 基础深度。 */
  BASIC("basic"),
  /** 高级深度。 */
  ADVANCED("advanced");

  private final String value;

  TavilySearchDepth(String value) {
    this.value = value;
  }

  /** @return API search_depth 参数 */
  public String getValue() {
    return value;
  }

  /**
   * @param raw 原始字符串
   * @return 枚举；无法识别时返回 {@link #ADVANCED}
   */
  public static TavilySearchDepth fromOrDefault(String raw) {
    if (raw == null || raw.isBlank()) {
      return ADVANCED;
    }
    String normalized = raw.trim().toLowerCase();
    for (TavilySearchDepth item : values()) {
      if (item.value.equals(normalized)) {
        return item;
      }
    }
    return ADVANCED;
  }
}
