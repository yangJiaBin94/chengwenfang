package com.chengwenfang.mode.hotnews.application.generation.hotnews;

/**
 * 热点配图种类（落库 kind 字段）。
 */
public enum HotNewsImageKind {
  /** 封面。 */
  COVER("cover"),
  /** 正文插图。 */
  INLINE("inline");

  /** 持久化/API 使用的 kind 值。 */
  private final String value;

  HotNewsImageKind(String value) {
    this.value = value;
  }

  /** @return kind 字面量 */
  public String getValue() {
    return value;
  }
}
