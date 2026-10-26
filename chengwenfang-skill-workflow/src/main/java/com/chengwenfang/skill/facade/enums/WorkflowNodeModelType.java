package com.chengwenfang.skill.facade.enums;

import cn.hutool.core.util.StrUtil;
import java.util.Locale;

/**
 * 工作流节点模型类型（图节点 config.modelType）。
 */
public enum WorkflowNodeModelType {
  /** 不调用模型。 */
  NONE("none"),
  /** 文本补全。 */
  TEXT("text"),
  /** 图像生成。 */
  IMAGE("image");

  /** 持久化 / JSON 字面量。 */
  private final String value;

  /**
   * @param value JSON 中的模型类型码
   */
  WorkflowNodeModelType(String value) {
    this.value = value;
  }

  /**
   * @return JSON / 配置字面量
   */
  public String getValue() {
    return value;
  }

  /**
   * 是否与给定类型码相等。
   *
   * @param raw 原始字符串
   * @return 匹配则为 true
   */
  public boolean matches(String raw) {
    return raw != null && value.equals(raw.trim());
  }

  /**
   * 解析模型类型；无法识别时回落 {@link #NONE}。
   *
   * @param raw 原始字符串
   * @return 枚举
   */
  public static WorkflowNodeModelType from(String raw) {
    if (StrUtil.isBlank(raw)) {
      return NONE;
    }
    String key = raw.trim().toLowerCase(Locale.ROOT);
    for (WorkflowNodeModelType modelType : values()) {
      if (modelType.value.equals(key)) {
        return modelType;
      }
    }
    return NONE;
  }
}
