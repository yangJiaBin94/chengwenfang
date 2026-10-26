package com.workbench.app.golden;

import cn.hutool.core.util.StrUtil;

/**
 * 黄金基线运行模式：录制或比对。
 */
public enum GoldenMode {
  /** 写入 baseline 文件。 */
  RECORD,
  /** 与已有 baseline 逐字比对（默认）。 */
  COMPARE;

  /**
   * 从系统属性 {@code golden.mode} 解析；缺省为比对。
   *
   * @return 运行模式
   */
  public static GoldenMode fromSystemProperty() {
    String raw = StrUtil.blankToDefault(System.getProperty("golden.mode"), "compare").trim();
    if ("record".equalsIgnoreCase(raw)) {
      return RECORD;
    }
    return COMPARE;
  }
}
