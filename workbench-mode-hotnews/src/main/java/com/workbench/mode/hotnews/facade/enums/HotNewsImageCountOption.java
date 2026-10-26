package com.workbench.mode.hotnews.facade.enums;

import cn.hutool.core.util.StrUtil;

/**
 * 热点正文插图张数档（不含封面；封面始终必出）。
 * <p>业务下限：除去封面至少 2 张正文插图。
 */
public enum HotNewsImageCountOption {
  /** 2～3 档 → 2 张正文插图。 */
  TWO_TO_THREE("2-3", 2),
  /** 3～5 档 → 3 张正文插图（默认推荐）。 */
  THREE_TO_FIVE("3-5", 3),
  /** 6～8 档 → 6 张正文插图。 */
  SIX_TO_EIGHT("6-8", 6);

  /** 档位字面量。 */
  private final String value;
  /** 正文插图张数（不含封面）。 */
  private final int inlineCount;

  HotNewsImageCountOption(String value, int inlineCount) {
    this.value = value;
    this.inlineCount = inlineCount;
  }

  /**
   * @return 档位字面量
   */
  public String getValue() {
    return value;
  }

  /**
   * @return 正文插图张数
   */
  public int getInlineCount() {
    return inlineCount;
  }

  /**
   * 默认推荐插图档。
   *
   * @return 3-5 档
   */
  public static HotNewsImageCountOption defaultOption() {
    return THREE_TO_FIVE;
  }

  /**
   * 正文插图业务下限（不含封面）。
   *
   * @return 至少张数
   */
  public static int minimumInlineCount() {
    return TWO_TO_THREE.inlineCount;
  }

  /**
   * 规范化插图档；兼容旧值 {@code 0}/{@code 1-2}，非法值回落默认。
   *
   * @param raw 原始档位
   * @return 合法字面量
   */
  public static String normalize(String raw) {
    if (StrUtil.isBlank(raw)) {
      return defaultOption().value;
    }
    String normalized = raw.trim();
    if ("0".equals(normalized) || "1-2".equals(normalized) || "1".equals(normalized)
        || "2".equals(normalized)) {
      return TWO_TO_THREE.value;
    }
    for (HotNewsImageCountOption item : values()) {
      if (item.value.equals(normalized)) {
        return item.value;
      }
    }
    try {
      int count = Integer.parseInt(normalized);
      if (count <= 2) {
        return TWO_TO_THREE.value;
      }
      if (count <= 5) {
        return THREE_TO_FIVE.value;
      }
      return SIX_TO_EIGHT.value;
    } catch (Exception ignored) {
      return defaultOption().value;
    }
  }

  /**
   * 解析档位对应正文插图张数；未知档位先规范化再解析。
   *
   * @param option 工作区档位
   * @return 正文插图数量（至少 {@link #minimumInlineCount()}）
   */
  public static int resolveInlineCount(String option) {
    String normalized = normalize(option);
    for (HotNewsImageCountOption item : values()) {
      if (item.value.equals(normalized)) {
        return item.inlineCount;
      }
    }
    return minimumInlineCount();
  }
}
