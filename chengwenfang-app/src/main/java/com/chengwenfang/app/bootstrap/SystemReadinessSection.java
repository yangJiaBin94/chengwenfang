package com.chengwenfang.app.bootstrap;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * 系统就绪面板分组。
 */
public enum SystemReadinessSection {
  /** 默认档：课程 + 知识库。 */
  DEFAULT_PROFILE("default"),
  /** 热点加装，不计入默认档是否通过。 */
  HOT_NEWS("hotnews"),
  /** Graph / Agent 等默认关闭项。 */
  OPTIONAL("optional");

  private final String value;

  SystemReadinessSection(String value) {
    this.value = value;
  }

  /**
   * API 用的分组码。
   *
   * @return 分组码
   */
  @JsonValue
  public String getValue() {
    return value;
  }
}
