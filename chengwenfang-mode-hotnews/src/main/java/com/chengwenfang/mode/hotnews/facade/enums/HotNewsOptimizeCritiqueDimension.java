package com.chengwenfang.mode.hotnews.facade.enums;

import cn.hutool.core.util.StrUtil;
import java.util.Locale;
import java.util.Optional;

/**
 * 热点原稿优化诊断分项维度。
 */
public enum HotNewsOptimizeCritiqueDimension {
  /** 叙述与出场顺序。 */
  NARRATIVE("narrative"),
  /** 语气。 */
  TONE("tone"),
  /** AI 味。 */
  AI_FLAVOR("aiFlavor"),
  /** 铺垫完整度。 */
  SETUP_COMPLETENESS("setupCompleteness"),
  /** 要点覆盖。 */
  COVERAGE("coverage"),
  /** 证据可核对（来源/链接）。 */
  EVIDENCE("evidence");

  /** 对外字面量（与模型 JSON 键一致）。 */
  private final String value;

  HotNewsOptimizeCritiqueDimension(String value) {
    this.value = value;
  }

  /**
   * @return 对外字面量
   */
  public String getValue() {
    return value;
  }

  /**
   * 解析维度键；未知则 empty。
   *
   * @param raw 原始键；可空
   * @return 枚举可选值
   */
  public static Optional<HotNewsOptimizeCritiqueDimension> from(String raw) {
    if (StrUtil.isBlank(raw)) {
      return Optional.empty();
    }
    String normalized = raw.trim();
    for (HotNewsOptimizeCritiqueDimension item : values()) {
      if (item.value.equalsIgnoreCase(normalized)) {
        return Optional.of(item);
      }
    }
    // 兼容旧模型输出 setup
    if ("setup".equalsIgnoreCase(normalized)) {
      return Optional.of(SETUP_COMPLETENESS);
    }
    return Optional.empty();
  }

  /**
   * 规范化维度键；未知则保留去空白原文小驼峰原样。
   *
   * @param raw 原始键
   * @return 规范字面量或修剪后原文
   */
  public static String normalize(String raw) {
    return from(raw).map(HotNewsOptimizeCritiqueDimension::getValue).orElse(StrUtil.trim(raw));
  }

  /**
   * @return 是否已知维度
   */
  public boolean matches(String raw) {
    return value.equalsIgnoreCase(StrUtil.trim(raw));
  }

  /**
   * @return Locale 无关的小写键（调试用）
   */
  public String lowerValue() {
    return value.toLowerCase(Locale.ROOT);
  }
}
