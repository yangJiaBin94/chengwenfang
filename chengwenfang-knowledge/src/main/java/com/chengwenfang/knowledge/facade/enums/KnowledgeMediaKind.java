package com.chengwenfang.knowledge.facade.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/** 知识库介质种类。 */
public enum KnowledgeMediaKind {
  /** Markdown 文章正文。 */
  ARTICLE_MARKDOWN("article_markdown"),
  /** 纯文本 / 摘要。 */
  PLAIN_TEXT("plain_text"),
  /** 通用文档（pdf/docx 等，二期抽取）。 */
  DOCUMENT("document"),
  /** 视频（索引转写文本）。 */
  VIDEO("video"),
  /** 网页正文。 */
  WEB_PAGE("web_page"),
  /** 图片（远期 vision 描述）。 */
  IMAGE("image");

  private final String value;

  KnowledgeMediaKind(String value) {
    this.value = value;
  }

  /** @return 持久化值 */
  @JsonValue
  public String getValue() {
    return value;
  }

  /**
   * 解析介质种类。
   *
   * @param raw 原始字符串
   * @return 枚举
   */
  @JsonCreator
  public static KnowledgeMediaKind from(String raw) {
    if (raw == null || raw.isBlank()) {
      throw new IllegalArgumentException("知识库介质种类不能为空");
    }
    String normalized = raw.trim().toLowerCase();
    for (KnowledgeMediaKind candidate : values()) {
      if (candidate.value.equals(normalized)) {
        return candidate;
      }
    }
    throw new IllegalArgumentException("未知知识库介质种类: " + raw);
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
