package com.workbench.notification.facade.enums;

/**
 * 通知级别（写入 {@code notifications.level}，值与原字面量一致）。
 */
public enum NotificationLevel {
  /** 成功。 */
  SUCCESS("success"),
  /** 失败/错误。 */
  ERROR("error");

  /** 持久化字面量。 */
  private final String value;

  /**
   * @param value 持久化字面量
   */
  NotificationLevel(String value) {
    this.value = value;
  }

  /**
   * @return 持久化字面量
   */
  public String getValue() {
    return value;
  }

  /**
   * 按字面量解析；未知时回退为 {@link #ERROR}。
   *
   * @param raw 原始级别
   * @return 枚举值
   */
  public static NotificationLevel from(String raw) {
    if (raw == null || raw.isBlank()) {
      return ERROR;
    }
    String normalized = raw.trim().toLowerCase();
    for (NotificationLevel candidate : values()) {
      if (candidate.value.equals(normalized)) {
        return candidate;
      }
    }
    return ERROR;
  }
}
