package com.chengwenfang.ai.facade.enums;

/** model_calls 调用状态。 */
public enum ModelCallStatus {
  /** 成功。 */
  SUCCEEDED("succeeded"),
  /** 失败。 */
  FAILED("failed");

  private final String value;

  ModelCallStatus(String value) {
    this.value = value;
  }

  public String getValue() {
    return value;
  }
}
