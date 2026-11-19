package com.chengwenfang.knowledge.facade.enums;

import cn.hutool.core.util.StrUtil;

/**
 * 知识库检索/问答模式。
 */
public enum KnowledgeRetrieveMode {
  /** 混合检索固定管线。 */
  HYBRID("hybrid"),
  /** 图局部检索。 */
  GRAPH_LOCAL("graph_local"),
  /** 图全局/社区摘要。 */
  GRAPH_GLOBAL("graph_global"),
  /** 有界代理迭代。 */
  AGENT("agent"),
  /** 证据不足拒答。 */
  ABSTAIN("abstain");

  /** 序列化值。 */
  private final String value;

  KnowledgeRetrieveMode(String value) {
    this.value = value;
  }

  /**
   * @return 序列化值
   */
  public String getValue() {
    return value;
  }

  /**
   * 解析模式；空白或未知时返回 {@link #HYBRID}。
   *
   * @param raw 原始字符串
   * @return 枚举
   */
  public static KnowledgeRetrieveMode from(String raw) {
    if (StrUtil.isBlank(raw)) {
      return HYBRID;
    }
    String normalized = raw.trim().toLowerCase();
    for (KnowledgeRetrieveMode mode : values()) {
      if (mode.value.equals(normalized)) {
        return mode;
      }
    }
    return HYBRID;
  }

  /**
   * 是否匹配。
   *
   * @param raw 原始值
   * @return 是否本枚举
   */
  public boolean matches(String raw) {
    return this == from(raw);
  }
}
