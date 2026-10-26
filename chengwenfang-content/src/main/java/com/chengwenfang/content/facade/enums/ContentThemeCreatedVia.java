package com.chengwenfang.content.facade.enums;

/** 主题创建来源。 */
public enum ContentThemeCreatedVia {
  SEED("seed"),
  WIZARD("wizard");

  private final String value;

  ContentThemeCreatedVia(String value) {
    this.value = value;
  }

  public String getValue() {
    return value;
  }

  /**
   * 是否与原始字符串匹配（忽略大小写）。
   *
   * @param raw 原始值
   * @return 匹配时 true
   */
  public boolean matches(String raw) {
    return raw != null && value.equalsIgnoreCase(raw.trim());
  }

  public static ContentThemeCreatedVia from(String raw) {
    if (raw == null || raw.isBlank()) {
      return null;
    }
    for (ContentThemeCreatedVia item : values()) {
      if (item.value.equalsIgnoreCase(raw.trim())) {
        return item;
      }
    }
    return null;
  }
}
