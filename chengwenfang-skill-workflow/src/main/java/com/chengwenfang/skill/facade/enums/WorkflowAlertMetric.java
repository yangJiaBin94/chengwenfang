package com.chengwenfang.skill.facade.enums;

/**
 * 工作流告警指标（写入 {@code workflow_alerts.metric}，值与原字面量一致）。
 */
public enum WorkflowAlertMetric {
  /** 运行失败。 */
  RUN_FAILED("run_failed");

  /** 持久化字面量。 */
  private final String value;

  /**
   * @param value 持久化字面量
   */
  WorkflowAlertMetric(String value) {
    this.value = value;
  }

  /**
   * @return 持久化字面量
   */
  public String getValue() {
    return value;
  }

  /**
   * 按字面量解析；未知时回退为 {@link #RUN_FAILED}。
   *
   * @param raw 原始指标
   * @return 枚举值
   */
  public static WorkflowAlertMetric from(String raw) {
    if (raw == null || raw.isBlank()) {
      return RUN_FAILED;
    }
    String normalized = raw.trim().toLowerCase();
    for (WorkflowAlertMetric candidate : values()) {
      if (candidate.value.equals(normalized)) {
        return candidate;
      }
    }
    return RUN_FAILED;
  }
}
