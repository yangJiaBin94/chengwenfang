package com.chengwenfang.sourcing.facade.enums;

import cn.hutool.core.util.StrUtil;
import java.util.Locale;

/**
 * 雷达信源生命周期：就绪 / 待解析 / 已弃用。
 */
public enum SourceLifecycleStatus {
  /** 可参与选题聚合与证据展示。 */
  READY("ready"),
  /** 视频等待解析完成。 */
  PENDING_PARSE("pending_parse"),
  /** 解析失败或营销向，不进证据。 */
  DISCARDED("discarded");

  private final String value;

  SourceLifecycleStatus(String value) {
    this.value = value;
  }

  /** @return 持久化小写词 */
  public String getValue() {
    return value;
  }

  /**
   * 解析生命周期；无法识别时返回 {@link #READY}。
   *
   * @param raw 原始字符串
   * @return 枚举
   */
  public static SourceLifecycleStatus from(String raw) {
    if (StrUtil.isBlank(raw)) {
      return READY;
    }
    String key = raw.trim().toLowerCase(Locale.ROOT);
    for (SourceLifecycleStatus status : values()) {
      if (status.value.equals(key)) {
        return status;
      }
    }
    return READY;
  }

  /** 是否可参与聚合/证据。 */
  public boolean isReady() {
    return this == READY;
  }

  /** 是否应跳过聚合。 */
  public boolean shouldSkipAggregate() {
    return this != READY;
  }
}
