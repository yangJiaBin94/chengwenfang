package com.chengwenfang.knowledge.facade.enums;

/** 知识库文档来源种类。 */
public enum KnowledgeSourceKind {
  /** 系统已生成文章（work_outputs）。 */
  WORK_OUTPUT("work_output"),
  /** 用户手动上传。 */
  MANUAL_UPLOAD("manual_upload"),
  /** 雷达信源条目。 */
  SOURCE_ITEM("source_item"),
  /** 选题资料池条目。 */
  RESEARCH_ITEM("research_item");

  private final String value;

  KnowledgeSourceKind(String value) {
    this.value = value;
  }

  /** @return 持久化值 */
  public String getValue() {
    return value;
  }

  /**
   * 解析来源种类。
   *
   * @param raw 原始字符串
   * @return 枚举
   */
  public static KnowledgeSourceKind from(String raw) {
    if (raw == null || raw.isBlank()) {
      throw new IllegalArgumentException("知识库来源种类不能为空");
    }
    String normalized = raw.trim().toLowerCase();
    for (KnowledgeSourceKind candidate : values()) {
      if (candidate.value.equals(normalized)) {
        return candidate;
      }
    }
    throw new IllegalArgumentException("未知知识库来源种类: " + raw);
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
