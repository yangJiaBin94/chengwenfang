package com.workbench.mode.hotnews.application.generation.radarcollect;

import cn.hutool.core.util.StrUtil;
import java.util.Locale;

/**
 * 雷达文章发布日来源（便于日志统计与排查）。
 */
public enum ArticlePublishDateSource {
  /** 渠道/搜索结果自带发布时间。 */
  CHANNEL("channel", "渠道自带"),
  /** 正文 dateInputText（Jina/Trafilatura）经正则片段 + AI。 */
  AI_CONTENT("ai_content", "正文AI"),
  /** Jina 抽空后补爬 HTML，再经正则片段 + AI。 */
  AI_HTML("ai_html", "HTML补爬AI"),
  /** Trafilatura metadata.date 回退解析。 */
  METADATA("metadata", "元数据回退"),
  /** 有正文但各路均未抽到日期。 */
  NONE("none", "未抽到"),
  /** 无可用正文，未进入发布日抽取。 */
  NO_CONTENT("no_content", "无正文跳过");

  private final String value;
  private final String label;

  /**
   * @param value 日志/统计用键
   * @param label 中文说明
   */
  ArticlePublishDateSource(String value, String label) {
    this.value = value;
    this.label = label;
  }

  /** @return 稳定键 */
  public String getValue() {
    return value;
  }

  /** @return 中文标签 */
  public String getLabel() {
    return label;
  }

  /**
   * @param raw 原始键
   * @return 枚举；无法识别时 {@link #NONE}
   */
  public static ArticlePublishDateSource from(String raw) {
    if (StrUtil.isBlank(raw)) {
      return NONE;
    }
    String key = raw.trim().toLowerCase(Locale.ROOT);
    for (ArticlePublishDateSource source : values()) {
      if (source.value.equals(key)) {
        return source;
      }
    }
    return NONE;
  }

  /** 是否已拿到可用发布日。 */
  public boolean isResolved() {
    return this == CHANNEL || this == AI_CONTENT || this == AI_HTML || this == METADATA;
  }
}
