package com.chengwenfang.app.bootstrap;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * 系统就绪单项状态。
 * <p>未加装与默认关闭不算故障，避免热点加装项把默认档显示成坏了。
 */
public enum SystemReadinessStatus {
  /** 已满足。 */
  PASS("pass"),
  /** 默认档必需项未满足。 */
  FAIL("fail"),
  /** 热点加装尚未安装，课程与知识库不依赖。 */
  NOT_INSTALLED("not_installed"),
  /** 可选能力保持关闭。 */
  OPTIONAL_OFF("optional_off");

  private final String value;

  SystemReadinessStatus(String value) {
    this.value = value;
  }

  /**
   * API 用的稳定小写值。
   *
   * @return 状态码
   */
  @JsonValue
  public String getValue() {
    return value;
  }

  /**
   * 是否与给定状态码相同。
   *
   * @param raw 状态码
   * @return 匹配则为 true
   */
  public boolean matches(String raw) {
    return value.equals(raw);
  }
}
