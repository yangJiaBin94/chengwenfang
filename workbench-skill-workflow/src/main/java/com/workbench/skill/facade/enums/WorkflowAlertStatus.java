package com.workbench.skill.facade.enums;

/**
 * 工作流告警状态（写入 {@code workflow_alerts.status}，值与原字面量一致）。
 */
public enum WorkflowAlertStatus {
  /** 未处理。 */
  OPEN("open"),
  /** 已解决。 */
  RESOLVED("resolved");

  /** 持久化字面量。 */
  private final String value;

  /**
   * @param value 持久化字面量
   */
  WorkflowAlertStatus(String value) {
    this.value = value;
  }

  /**
   * @return 持久化字面量
   */
  public String getValue() {
    return value;
  }

  /**
   * 按字面量解析；未知时回退为 {@link #OPEN}。
   *
   * @param raw 原始状态
   * @return 枚举值
   */
  public static WorkflowAlertStatus from(String raw) {
    if (raw == null || raw.isBlank()) {
      return OPEN;
    }
    String normalized = raw.trim().toLowerCase();
    for (WorkflowAlertStatus candidate : values()) {
      if (candidate.value.equals(normalized)) {
        return candidate;
      }
    }
    return OPEN;
  }
}
