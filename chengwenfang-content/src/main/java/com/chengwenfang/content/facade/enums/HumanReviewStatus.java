package com.chengwenfang.content.facade.enums;

/** 人工审核结论。 */
public enum HumanReviewStatus {
  /** 通过。 */
  APPROVED("approved"),
  /** 驳回。 */
  REJECTED("rejected");

  private final String value;

  HumanReviewStatus(String value) {
    this.value = value;
  }

  public String getValue() {
    return value;
  }

  public static HumanReviewStatus from(String raw) {
    if (raw == null || raw.isBlank()) {
      throw new IllegalArgumentException("人工审核状态不能为空");
    }
    String normalized = raw.trim().toLowerCase();
    for (HumanReviewStatus candidate : values()) {
      if (candidate.value.equals(normalized)) {
        return candidate;
      }
    }
    throw new IllegalArgumentException("未知人工审核状态: " + raw);
  }
}
