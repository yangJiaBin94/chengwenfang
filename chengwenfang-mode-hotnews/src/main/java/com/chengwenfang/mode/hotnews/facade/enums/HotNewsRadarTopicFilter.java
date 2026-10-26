package com.chengwenfang.mode.hotnews.facade.enums;

import cn.hutool.core.util.StrUtil;
import java.util.Locale;

/**
 * 热点雷达选题列表筛选（闭集）。
 */
public enum HotNewsRadarTopicFilter {
  /** 仅当天创建/最近出现的选题。 */
  TODAY("today"),
  /** 雷达池全部（仍排除非当日确认的已选题）。 */
  ALL("all"),
  /** 仅候选。 */
  CANDIDATE("candidate"),
  /** 仅当日已确认（含写作中、已完成）。 */
  CONFIRMED("confirmed");

  private final String value;

  HotNewsRadarTopicFilter(String value) {
    this.value = value;
  }

  /** @return API 用小写词 */
  public String getValue() {
    return value;
  }

  /**
   * 解析筛选词；无法识别时回退为 {@link #TODAY}。
   *
   * @param raw 原始字符串
   * @return 枚举
   */
  public static HotNewsRadarTopicFilter from(String raw) {
    if (StrUtil.isBlank(raw)) {
      return TODAY;
    }
    String key = raw.trim().toLowerCase(Locale.ROOT);
    for (HotNewsRadarTopicFilter filter : values()) {
      if (filter.value.equals(key)) {
        return filter;
      }
    }
    return TODAY;
  }

  /** 是否为「仅候选」。 */
  public boolean isCandidate() {
    return this == CANDIDATE;
  }

  /** 是否为「仅已确认」。 */
  public boolean isConfirmed() {
    return this == CONFIRMED;
  }

  /** 是否为「仅当天」。 */
  public boolean isToday() {
    return this == TODAY;
  }
}
