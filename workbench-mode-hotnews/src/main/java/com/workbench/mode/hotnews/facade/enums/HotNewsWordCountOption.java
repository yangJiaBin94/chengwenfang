package com.workbench.mode.hotnews.facade.enums;

import cn.hutool.core.util.StrUtil;

/**
 * 热点正文字数档（骨架推荐与工作区选项）。
 */
public enum HotNewsWordCountOption {
  /** 短篇。 */
  SHORT("1500-2500"),
  /** 中篇（默认推荐）。 */
  MEDIUM("2500-4000"),
  /** 长篇。 */
  LONG("4000-6000");

  /** 档位字面量。 */
  private final String value;

  HotNewsWordCountOption(String value) {
    this.value = value;
  }

  /**
   * @return 档位字面量
   */
  public String getValue() {
    return value;
  }

  /**
   * 默认推荐字数档。
   *
   * @return 中篇档
   */
  public static HotNewsWordCountOption defaultOption() {
    return MEDIUM;
  }

  /**
   * 规范化字数档；非法值回落默认。
   *
   * @param raw 原始档位
   * @return 合法字面量
   */
  public static String normalize(String raw) {
    if (StrUtil.isBlank(raw)) {
      return defaultOption().value;
    }
    String normalized = raw.trim();
    for (HotNewsWordCountOption item : values()) {
      if (item.value.equals(normalized)) {
        return item.value;
      }
    }
    return defaultOption().value;
  }
}
