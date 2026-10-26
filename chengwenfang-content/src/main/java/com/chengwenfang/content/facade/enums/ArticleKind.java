package com.chengwenfang.content.facade.enums;

/** 文章 kind：原稿 / 公众号稿。 */
public enum ArticleKind {
  /** 课程原稿。 */
  COURSE_DRAFT("course_draft"),
  /** 公众号文章。 */
  WECHAT_ARTICLE("wechat_article");

  private final String value;

  ArticleKind(String value) {
    this.value = value;
  }

  public String getValue() {
    return value;
  }

  public static ArticleKind from(String raw) {
    if (raw == null || raw.isBlank()) {
      throw new IllegalArgumentException("文章 kind 不能为空");
    }
    String normalized = raw.trim().toLowerCase();
    for (ArticleKind candidate : values()) {
      if (candidate.value.equals(normalized)) {
        return candidate;
      }
    }
    throw new IllegalArgumentException("未知文章 kind: " + raw);
  }
}
