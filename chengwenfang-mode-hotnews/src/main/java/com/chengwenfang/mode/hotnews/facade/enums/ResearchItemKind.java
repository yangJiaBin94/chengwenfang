package com.chengwenfang.mode.hotnews.facade.enums;

import java.util.Locale;

/** 调研材料条目来源类型（闭集）。 */
public enum ResearchItemKind {
  /** 选题已关联来源。 */
  SOURCE("source", "热点原始来源"),
  /** 网页检索补抓。 */
  WEB("web", "网页检索"),
  /** 用户手动粘贴链接添加。 */
  MANUAL("manual", "手动添加");

  private final String value;
  /** 资料池展示用渠道标签。 */
  private final String sourceLabel;

  ResearchItemKind(String value, String sourceLabel) {
    this.value = value;
    this.sourceLabel = sourceLabel;
  }

  /** 落库/对外使用的字面量。 */
  public String getValue() {
    return value;
  }

  /** 前端展示的渠道标签。 */
  public String getSourceLabel() {
    return sourceLabel;
  }

  /** 是否为手动添加（资料池自动刷新时需保留）。 */
  public boolean isManual() {
    return this == MANUAL;
  }

  /**
   * 从字面量解析；未知值回退为 {@link #WEB}。
   *
   * @param raw 原始字符串
   * @return 对应枚举，空白或未知时为 WEB
   */
  public static ResearchItemKind from(String raw) {
    if (raw == null || raw.isBlank()) {
      return WEB;
    }
    String normalized = raw.trim().toLowerCase(Locale.ROOT);
    for (ResearchItemKind kind : values()) {
      if (kind.value.equals(normalized)) {
        return kind;
      }
    }
    return WEB;
  }
}
