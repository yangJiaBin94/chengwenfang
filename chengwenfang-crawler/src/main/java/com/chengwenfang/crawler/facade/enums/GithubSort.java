package com.chengwenfang.crawler.facade.enums;

/**
 * GitHub 仓库搜索排序字段。
 */
public enum GithubSort {
  /** 按 Star 数。 */
  STARS("stars"),
  /** 按更新时间。 */
  UPDATED("updated"),
  /** 按 Fork 数。 */
  FORKS("forks");

  private final String value;

  GithubSort(String value) {
    this.value = value;
  }

  /** @return API sort 参数值 */
  public String getValue() {
    return value;
  }

  /**
   * @param raw 原始字符串
   * @return 枚举；无法识别时返回 {@link #STARS}
   */
  public static GithubSort fromOrDefault(String raw) {
    if (raw == null || raw.isBlank()) {
      return STARS;
    }
    String normalized = raw.trim().toLowerCase();
    for (GithubSort item : values()) {
      if (item.value.equals(normalized)) {
        return item;
      }
    }
    return STARS;
  }
}
