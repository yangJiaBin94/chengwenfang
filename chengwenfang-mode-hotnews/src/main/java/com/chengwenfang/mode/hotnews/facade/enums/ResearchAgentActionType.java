package com.chengwenfang.mode.hotnews.facade.enums;

import cn.hutool.core.util.StrUtil;

/**
 * 选题资料补充 Agent 动作类型。
 */
public enum ResearchAgentActionType {
  /** 发起元搜索。 */
  SEARCH("search"),
  /** 抓取已命中 URL 的正文摘要。 */
  FETCH("fetch"),
  /** 结束并提交选用条目。 */
  FINISH("finish");

  private final String value;

  ResearchAgentActionType(String value) {
    this.value = value;
  }

  /**
   * @return 协议字面量
   */
  public String getValue() {
    return value;
  }

  /**
   * 是否与给定字面量匹配。
   *
   * @param raw 原始字符串
   * @return 匹配时 true
   */
  public boolean matches(String raw) {
    return value.equalsIgnoreCase(StrUtil.trim(raw));
  }

  /**
   * 解析动作类型。
   *
   * @param raw 原始字符串
   * @return 枚举；未知时 null
   */
  public static ResearchAgentActionType from(String raw) {
    if (StrUtil.isBlank(raw)) {
      return null;
    }
    String key = raw.trim();
    for (ResearchAgentActionType type : values()) {
      if (type.value.equalsIgnoreCase(key)) {
        return type;
      }
    }
    return null;
  }
}
