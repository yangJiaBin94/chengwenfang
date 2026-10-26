package com.chengwenfang.job.facade.enums;

/** 异步任务生命周期状态。 */
public enum JobStatus {
  /** 排队待执行。 */
  PENDING("pending"),
  /** 执行中。 */
  RUNNING("running"),
  /** 成功。 */
  SUCCEEDED("succeeded"),
  /** 失败。 */
  FAILED("failed"),
  /** 已取消。 */
  CANCELLED("cancelled"),
  /** 队列中（部分视图用）。 */
  QUEUED("queued");

  private final String value;

  JobStatus(String value) {
    this.value = value;
  }

  public String getValue() {
    return value;
  }

  public static JobStatus from(String raw) {
    if (raw == null || raw.isBlank()) {
      return PENDING;
    }
    String normalized = raw.trim().toLowerCase();
    for (JobStatus candidate : values()) {
      if (candidate.value.equals(normalized)) {
        return candidate;
      }
    }
    throw new IllegalArgumentException("未知任务状态: " + raw);
  }

  public boolean isTerminal() {
    return this == SUCCEEDED || this == FAILED || this == CANCELLED;
  }
}
