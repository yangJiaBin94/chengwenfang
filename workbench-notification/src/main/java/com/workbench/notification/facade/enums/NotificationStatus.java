package com.workbench.notification.facade.enums;

/**
 * 通知已读状态（写入 {@code notifications.status}，值与原字面量一致）。
 */
public enum NotificationStatus {
  /** 未读。 */
  UNREAD("unread"),
  /** 已读。 */
  READ("read");

  /** 持久化字面量。 */
  private final String value;

  /**
   * @param value 持久化字面量
   */
  NotificationStatus(String value) {
    this.value = value;
  }

  /**
   * @return 持久化字面量
   */
  public String getValue() {
    return value;
  }

  /**
   * 按字面量解析；未知时回退为 {@link #UNREAD}。
   *
   * @param raw 原始状态
   * @return 枚举值
   */
  public static NotificationStatus from(String raw) {
    if (raw == null || raw.isBlank()) {
      return UNREAD;
    }
    String normalized = raw.trim().toLowerCase();
    for (NotificationStatus candidate : values()) {
      if (candidate.value.equals(normalized)) {
        return candidate;
      }
    }
    return UNREAD;
  }

  /**
   * 是否未读。
   *
   * @param raw 原始状态
   * @return 未读为 true
   */
  public static boolean isUnread(String raw) {
    return UNREAD == from(raw);
  }
}
