package com.chengwenfang.mode.hotnews.facade.enums;

import cn.hutool.core.util.StrUtil;
import java.util.Locale;

/**
 * 热点雷达选题列表排序（闭集）。
 */
public enum HotNewsRadarTopicSort {
  /** 按写作价值分。 */
  WRITING("writing"),
  /** 按热度分。 */
  HEAT("heat");

  private final String value;

  HotNewsRadarTopicSort(String value) {
    this.value = value;
  }

  /** @return API 用小写词 */
  public String getValue() {
    return value;
  }

  /**
   * 解析排序词；无法识别时回退为 {@link #WRITING}。
   *
   * @param raw 原始字符串
   * @return 枚举
   */
  public static HotNewsRadarTopicSort from(String raw) {
    if (StrUtil.isBlank(raw)) {
      return WRITING;
    }
    String key = raw.trim().toLowerCase(Locale.ROOT);
    for (HotNewsRadarTopicSort sort : values()) {
      if (sort.value.equals(key)) {
        return sort;
      }
    }
    return WRITING;
  }

  /** 是否按热度排序。 */
  public boolean isHeat() {
    return this == HEAT;
  }
}
