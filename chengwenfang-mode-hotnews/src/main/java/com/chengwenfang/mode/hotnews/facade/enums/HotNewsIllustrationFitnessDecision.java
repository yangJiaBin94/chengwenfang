package com.chengwenfang.mode.hotnews.facade.enums;

import cn.hutool.core.util.StrUtil;
import java.util.Locale;

/**
 * 改写后插图适配裁定：复用或重生。
 */
public enum HotNewsIllustrationFitnessDecision {
  /** 提示词与画面仍贴合，复用原图。 */
  REUSE("reuse"),
  /** 内容或提示词已变，需重新生成。 */
  REGENERATE("regenerate");

  /** 对外字面量。 */
  private final String value;

  HotNewsIllustrationFitnessDecision(String value) {
    this.value = value;
  }

  /**
   * @return 对外字面量
   */
  public String getValue() {
    return value;
  }

  /**
   * 是否与给定字面量匹配。
   *
   * @param raw 原始决策串
   * @return 匹配则为 true
   */
  public boolean matches(String raw) {
    return value.equalsIgnoreCase(StrUtil.trim(raw));
  }

  /**
   * 解析模型/存量决策；含 reuse 视为复用，其余默认重生。
   *
   * @param raw 原始决策串；可空
   * @return 枚举值
   */
  public static HotNewsIllustrationFitnessDecision from(String raw) {
    if (StrUtil.isBlank(raw)) {
      return REGENERATE;
    }
    String normalized = raw.trim().toLowerCase(Locale.ROOT);
    if (normalized.contains(REUSE.value)) {
      return REUSE;
    }
    return REGENERATE;
  }
}
