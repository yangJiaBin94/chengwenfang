package com.chengwenfang.ai.facade.enums;

import cn.hutool.core.util.StrUtil;

/**
 * 补全请求的联网搜索模式（能力抽象；具体厂商由适配器映射）。
 */
public enum WebSearchMode {
  /** 不开启联网工具。 */
  OFF("off"),
  /** 普通联网搜索（不强制深度思考）。 */
  ON("on"),
  /** 深度思考搜索（多步搜读）。 */
  DEEP("deep");

  /** 序列化值。 */
  private final String value;

  WebSearchMode(String value) {
    this.value = value;
  }

  /**
   * @return 序列化值
   */
  public String getValue() {
    return value;
  }

  /**
   * 是否开启联网。
   *
   * @return OFF 以外为 true
   */
  public boolean isEnabled() {
    return this != OFF;
  }

  /**
   * 解析；未知或空白视为 OFF。
   *
   * @param raw 原始字符串
   * @return 枚举
   */
  public static WebSearchMode from(String raw) {
    if (StrUtil.isBlank(raw)) {
      return OFF;
    }
    String normalized = raw.trim().toLowerCase();
    for (WebSearchMode mode : values()) {
      if (mode.value.equals(normalized) || mode.name().equalsIgnoreCase(normalized)) {
        return mode;
      }
    }
    return OFF;
  }
}
