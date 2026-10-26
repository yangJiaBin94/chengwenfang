package com.chengwenfang.sourcing.facade.enums;

/**
 * 单次抓取中各频道子运行状态。
 */
public enum ChannelRunStatus {
  /** 成功。 */
  SUCCEEDED("succeeded"),
  /** 失败。 */
  FAILED("failed");

  private final String value;

  ChannelRunStatus(String value) {
    this.value = value;
  }

  /** @return 持久化/视图字面量 */
  public String getValue() {
    return value;
  }

  /**
   * @param raw 原始字符串
   * @return 枚举；无法识别时返回 {@link #SUCCEEDED}
   */
  public static ChannelRunStatus fromOrDefault(String raw) {
    if (raw == null || raw.isBlank()) {
      return SUCCEEDED;
    }
    String normalized = raw.trim().toLowerCase();
    for (ChannelRunStatus item : values()) {
      if (item.value.equals(normalized)) {
        return item;
      }
    }
    return SUCCEEDED;
  }
}
