package com.workbench.crawler.facade.enums;

import cn.hutool.core.util.StrUtil;

/**
 * 文章正文获取来源层级。
 */
public enum ArticleContentSource {
  /** Jina Reader。 */
  JINA("jina"),
  /** 本机 Trafilatura。 */
  TRAFILATURA("trafilatura"),
  /** Lightpanda 渲染后再抽取。 */
  LIGHTPANDA("lightpanda"),
  /** 搜索摘要兜底。 */
  SUMMARY("summary"),
  /** 无可用正文。 */
  NONE("none");

  /** 持久化/日志值。 */
  private final String value;

  /**
   * @param value 词表值
   */
  ArticleContentSource(String value) {
    this.value = value;
  }

  /**
   * @return 词表值
   */
  public String getValue() {
    return value;
  }

  /**
   * @param raw 原始字符串
   * @return 枚举；无法识别时 {@link #NONE}
   */
  public static ArticleContentSource from(String raw) {
    if (StrUtil.isBlank(raw)) {
      return NONE;
    }
    String normalized = raw.trim().toLowerCase();
    for (ArticleContentSource source : values()) {
      if (source.value.equals(normalized)) {
        return source;
      }
    }
    return NONE;
  }

  /**
   * @param raw 原始字符串
   * @return 是否匹配本枚举
   */
  public boolean matches(String raw) {
    return this == from(raw);
  }

  /**
   * @return 是否已拿到可用正文来源（非 none）
   */
  public boolean isResolved() {
    return this != NONE;
  }
}
