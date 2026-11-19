package com.chengwenfang.knowledge.facade.enums;

/**
 * 归一化正文的切块形态：由抽取器声明，索引管线按此选 chunker，不按原始扩展名。
 */
public enum KnowledgeChunkProfile {
  /** 含可用 ATX 标题的 Markdown，走结构切块。 */
  MARKDOWN_STRUCTURED("markdown_structured"),
  /** 无结构纯文本，走段落滑窗。 */
  PLAIN("plain"),
  /** 表格按行切块。 */
  TABLE_ROWS("table_rows"),
  /** 演示文稿按页切块。 */
  SLIDE("slide");

  private final String value;

  KnowledgeChunkProfile(String value) {
    this.value = value;
  }

  /**
   * @return 持久化 / 日志值
   */
  public String getValue() {
    return value;
  }

  /**
   * 解析切块形态。
   *
   * @param raw 原始字符串
   * @return 枚举
   */
  public static KnowledgeChunkProfile from(String raw) {
    if (raw == null || raw.isBlank()) {
      throw new IllegalArgumentException("切块形态不能为空");
    }
    String normalized = raw.trim().toLowerCase();
    for (KnowledgeChunkProfile candidate : values()) {
      if (candidate.value.equals(normalized)) {
        return candidate;
      }
    }
    throw new IllegalArgumentException("未知切块形态: " + raw);
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
