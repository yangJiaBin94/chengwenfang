package com.workbench.skill.facade.enums;

/** 工作流 run / node-run 状态。 */
public enum WorkflowRunStatus {
  /** 排队。 */
  QUEUED("queued"),
  /** 运行中。 */
  RUNNING("running"),
  /** 成功。 */
  SUCCEEDED("succeeded"),
  /** 失败。 */
  FAILED("failed");

  /** 持久化字面量。 */
  private final String value;

  /**
   * @param value 持久化字面量
   */
  WorkflowRunStatus(String value) {
    this.value = value;
  }

  /**
   * @return 持久化字面量
   */
  public String getValue() {
    return value;
  }

  /**
   * 按字面量解析；空白或未知时回退为 {@link #FAILED}。
   *
   * @param raw 原始状态
   * @return 枚举值
   */
  public static WorkflowRunStatus from(String raw) {
    if (raw == null || raw.isBlank()) {
      return FAILED;
    }
    String normalized = raw.trim().toLowerCase();
    for (WorkflowRunStatus candidate : values()) {
      if (candidate.value.equals(normalized)) {
        return candidate;
      }
    }
    return FAILED;
  }
}
