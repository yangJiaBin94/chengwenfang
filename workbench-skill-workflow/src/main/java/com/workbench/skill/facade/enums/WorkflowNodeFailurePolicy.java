package com.workbench.skill.facade.enums;

import cn.hutool.core.util.StrUtil;
import java.util.Locale;

/**
 * 工作流节点失败策略（图节点 config.failurePolicy）。
 */
public enum WorkflowNodeFailurePolicy {
  /** 首次失败即停止整图。 */
  STOP("stop"),
  /** 按 maxAttempts 重试，仍失败则停止。 */
  RETRY_THEN_STOP("retry_then_stop"),
  /** 记录警告后继续下游节点。 */
  CONTINUE_WITH_WARNING("continue_with_warning");

  /** 持久化 / JSON 字面量。 */
  private final String value;

  /**
   * @param value JSON 中的策略码
   */
  WorkflowNodeFailurePolicy(String value) {
    this.value = value;
  }

  /**
   * @return JSON / 配置字面量
   */
  public String getValue() {
    return value;
  }

  /**
   * 是否与给定策略码相等。
   *
   * @param raw 原始字符串
   * @return 匹配则为 true
   */
  public boolean matches(String raw) {
    return raw != null && value.equals(raw.trim());
  }

  /**
   * 解析失败策略；无法识别时回落 {@link #STOP}。
   *
   * @param raw 原始字符串
   * @return 枚举
   */
  public static WorkflowNodeFailurePolicy from(String raw) {
    if (StrUtil.isBlank(raw)) {
      return STOP;
    }
    String key = raw.trim().toLowerCase(Locale.ROOT);
    for (WorkflowNodeFailurePolicy policy : values()) {
      if (policy.value.equals(key)) {
        return policy;
      }
    }
    return STOP;
  }
}
