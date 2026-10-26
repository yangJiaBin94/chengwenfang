package com.chengwenfang.sourcing.facade.enums;

import cn.hutool.core.util.StrUtil;
import java.util.Locale;

/**
 * 雷达信源内容意图：知识向或营销向。
 */
public enum SourceContentIntent {
  /** 科普/资讯/机理等知识向。 */
  KNOWLEDGE("knowledge"),
  /** 推销、带货、销售导向。 */
  MARKETING("marketing");

  private final String value;

  SourceContentIntent(String value) {
    this.value = value;
  }

  /** @return 持久化小写词 */
  public String getValue() {
    return value;
  }

  /**
   * 解析意图；无法识别时返回 {@link #KNOWLEDGE}。
   *
   * @param raw 原始字符串
   * @return 枚举
   */
  public static SourceContentIntent from(String raw) {
    if (StrUtil.isBlank(raw)) {
      return KNOWLEDGE;
    }
    String key = raw.trim().toLowerCase(Locale.ROOT);
    for (SourceContentIntent intent : values()) {
      if (intent.value.equals(key)) {
        return intent;
      }
    }
    return KNOWLEDGE;
  }

  /** 是否营销向。 */
  public boolean isMarketing() {
    return this == MARKETING;
  }
}
