package com.chengwenfang.content.facade.enums;

import cn.hutool.core.util.StrUtil;
import java.util.Locale;
import java.util.Optional;

/**
 * 热点雷达采集策略模式。
 */
public enum HotNewsCollectStrategyMode {
  /** 固定搜索词 + 固定渠道。 */
  STRUCTURED("structured"),
  /** 由意图衍生多轮搜索计划。 */
  UNSTRUCTURED("unstructured");

  /** 持久化值。 */
  private final String value;

  HotNewsCollectStrategyMode(String value) {
    this.value = value;
  }

  /**
   * @return 持久化值
   */
  public String getValue() {
    return value;
  }

  /**
   * 是否与给定值匹配。
   *
   * @param raw 原始值
   * @return 匹配则为 true
   */
  public boolean matches(String raw) {
    return value.equalsIgnoreCase(StrUtil.trim(StrUtil.nullToEmpty(raw)));
  }

  /**
   * 解析策略模式。
   *
   * @param raw 原始值
   * @return 枚举；未知则 empty
   */
  public static Optional<HotNewsCollectStrategyMode> from(String raw) {
    if (StrUtil.isBlank(raw)) {
      return Optional.empty();
    }
    String normalized = raw.trim().toLowerCase(Locale.ROOT);
    for (HotNewsCollectStrategyMode mode : values()) {
      if (mode.value.equals(normalized)) {
        return Optional.of(mode);
      }
    }
    return Optional.empty();
  }

  /**
   * 是否非结构化模式。
   *
   * @param raw 原始值
   * @return true 表示 UNSTRUCTURED
   */
  public static boolean isUnstructured(String raw) {
    return UNSTRUCTURED.matches(raw);
  }
}
