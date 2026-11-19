package com.chengwenfang.knowledge.facade.enums;

import cn.hutool.core.util.StrUtil;

/**
 * 知识库检索降级原因码。
 */
public enum KnowledgeDegradedReason {
  /** 向量库降级为内存实现。 */
  VECTOR_FALLBACK_MEMORY("vector_fallback_memory"),
  /** 知识图谱不可用。 */
  GRAPH_UNAVAILABLE("graph_unavailable");

  /** 序列化值。 */
  private final String value;

  KnowledgeDegradedReason(String value) {
    this.value = value;
  }

  /**
   * @return 序列化值
   */
  public String getValue() {
    return value;
  }

  /**
   * 解析降级原因；空白或未知时返回 {@code null}。
   *
   * @param raw 原始字符串
   * @return 枚举或 null
   */
  public static KnowledgeDegradedReason from(String raw) {
    if (StrUtil.isBlank(raw)) {
      return null;
    }
    String normalized = raw.trim().toLowerCase();
    for (KnowledgeDegradedReason reason : values()) {
      if (reason.value.equals(normalized)) {
        return reason;
      }
    }
    return null;
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
