package com.workbench.crawler.facade.enums;

/**
 * GitHub 搜索时间口径；实际抓取固定使用 {@link #UPDATED}。
 */
public enum GithubDateField {
  /** 按仓库创建时间（历史配置兼容）。 */
  CREATED("created"),
  /** 按仓库更新时间（默认口径）。 */
  UPDATED("updated");

  private final String value;

  GithubDateField(String value) {
    this.value = value;
  }

  /** @return 配置字面量 */
  public String getValue() {
    return value;
  }

  /**
   * @param raw 原始字符串
   * @return 枚举；无法识别时返回 {@link #UPDATED}
   */
  public static GithubDateField fromOrDefault(String raw) {
    if (raw == null || raw.isBlank()) {
      return UPDATED;
    }
    String normalized = raw.trim().toLowerCase();
    for (GithubDateField item : values()) {
      if (item.value.equals(normalized)) {
        return item;
      }
    }
    return UPDATED;
  }
}
