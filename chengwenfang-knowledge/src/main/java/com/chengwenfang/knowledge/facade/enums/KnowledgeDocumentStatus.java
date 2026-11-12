package com.chengwenfang.knowledge.facade.enums;

/** 知识库文档索引状态。 */
public enum KnowledgeDocumentStatus {
  /** 已登记待索引。 */
  PENDING("pending"),
  /** 索引中。 */
  INDEXING("indexing"),
  /** 可检索。 */
  READY("ready"),
  /** 来源已变更待重建。 */
  STALE("stale"),
  /** 索引失败。 */
  FAILED("failed");

  private final String value;

  KnowledgeDocumentStatus(String value) {
    this.value = value;
  }

  /** @return 持久化值 */
  public String getValue() {
    return value;
  }

  /**
   * 解析状态。
   *
   * @param raw 原始字符串
   * @return 枚举
   */
  public static KnowledgeDocumentStatus from(String raw) {
    if (raw == null || raw.isBlank()) {
      throw new IllegalArgumentException("知识库文档状态不能为空");
    }
    String normalized = raw.trim().toLowerCase();
    for (KnowledgeDocumentStatus candidate : values()) {
      if (candidate.value.equals(normalized)) {
        return candidate;
      }
    }
    throw new IllegalArgumentException("未知知识库文档状态: " + raw);
  }

  /**
   * 是否匹配。
   *
   * @param raw 原始值
   * @return 匹配时 true
   */
  public boolean matches(String raw) {
    return raw != null && value.equalsIgnoreCase(raw.trim());
  }
}
