package com.workbench.content.facade.enums;

/** 内容主题生命周期状态。 */
public enum ContentThemeStatus {
  DRAFT("draft"),
  READY_FOR_REVIEW("ready_for_review"),
  ENABLED("enabled"),
  DISABLED("disabled");

  private final String value;

  ContentThemeStatus(String value) {
    this.value = value;
  }

  public String getValue() {
    return value;
  }

  /**
   * 是否与原始状态字符串相等（忽略大小写）。
   *
   * @param raw 状态字符串
   * @return 匹配则为 true
   */
  public boolean matches(String raw) {
    return raw != null && value.equalsIgnoreCase(raw.trim());
  }

  /** @param raw 原始字符串；未知时返回 null */
  public static ContentThemeStatus from(String raw) {
    if (raw == null || raw.isBlank()) {
      return null;
    }
    for (ContentThemeStatus item : values()) {
      if (item.value.equalsIgnoreCase(raw.trim())) {
        return item;
      }
    }
    return null;
  }
}
