package com.workbench.quality.facade.enums;

/** 热点文章审核运行 / 规则项状态。 */
public enum ReviewStatus {
  /** 待处理。 */
  PENDING("pending"),
  /** 通过。 */
  PASSED("passed"),
  /** 失败。 */
  FAILED("failed"),
  /** 警告（非阻断）。 */
  WARN("warn"),
  /** 驳回。 */
  REJECTED("rejected");

  private final String value;

  ReviewStatus(String value) {
    this.value = value;
  }

  public String getValue() {
    return value;
  }

  /**
   * 解析状态；兼容历史 {@code pass}/{@code fail}/{@code warning}。
   *
   * @param raw 原始状态
   * @return 枚举值
   */
  public static ReviewStatus from(String raw) {
    if (raw == null || raw.isBlank()) {
      return PENDING;
    }
    String normalized = raw.trim().toLowerCase();
    if ("pass".equals(normalized)) {
      return PASSED;
    }
    if ("fail".equals(normalized)) {
      return FAILED;
    }
    if ("warning".equals(normalized)) {
      return WARN;
    }
    for (ReviewStatus candidate : values()) {
      if (candidate.value.equals(normalized)) {
        return candidate;
      }
    }
    return PENDING;
  }

  /**
   * 是否为失败类状态。
   *
   * @return 失败或驳回为 true
   */
  public boolean isFailLike() {
    return this == FAILED || this == REJECTED;
  }

  /**
   * 是否为警告类状态。
   *
   * @return 警告为 true
   */
  public boolean isWarnLike() {
    return this == WARN;
  }
}
