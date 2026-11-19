package com.chengwenfang.knowledge.facade.enums;

import cn.hutool.core.util.StrUtil;

/**
 * 知识库拒答原因码。
 */
public enum KnowledgeAbstainReason {
  /** 证据不足。 */
  INSUFFICIENT_EVIDENCE("insufficient_evidence"),
  /** 忠实度校验失败。 */
  FAITHFULNESS_FAILED("faithfulness_failed");

  /** 序列化值。 */
  private final String value;

  KnowledgeAbstainReason(String value) {
    this.value = value;
  }

  /**
   * @return 序列化值
   */
  public String getValue() {
    return value;
  }

  /**
   * 解析拒答原因；空白或未知时返回 {@code null}。
   *
   * @param raw 原始字符串
   * @return 枚举或 null
   */
  public static KnowledgeAbstainReason from(String raw) {
    if (StrUtil.isBlank(raw)) {
      return null;
    }
    String normalized = raw.trim().toLowerCase();
    for (KnowledgeAbstainReason reason : values()) {
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
