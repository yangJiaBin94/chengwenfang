package com.chengwenfang.knowledge.facade.enums;

import cn.hutool.core.util.StrUtil;

/**
 * 答案反馈评级。
 */
public enum KnowledgeFeedbackRating {
  /** 赞。 */
  UP("up"),
  /** 踩。 */
  DOWN("down");

  /** 序列化值。 */
  private final String value;

  KnowledgeFeedbackRating(String value) {
    this.value = value;
  }

  /**
   * @return 序列化值
   */
  public String getValue() {
    return value;
  }

  /**
   * 解析评级；未知返回 null。
   *
   * @param raw 原始值
   * @return 枚举
   */
  public static KnowledgeFeedbackRating from(String raw) {
    if (StrUtil.isBlank(raw)) {
      return null;
    }
    String normalized = raw.trim().toLowerCase();
    for (KnowledgeFeedbackRating rating : values()) {
      if (rating.value.equals(normalized)) {
        return rating;
      }
    }
    return null;
  }

  /**
   * 是否匹配。
   *
   * @param raw 原始值
   * @return 是否本枚举
   */
  public boolean matches(String raw) {
    return this == from(raw);
  }
}
