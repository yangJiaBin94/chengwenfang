package com.workbench.skill.facade.enums;

import cn.hutool.core.util.StrUtil;
import java.util.Locale;

/**
 * 工作流定义的启用开关。是否已发布版本看 {@code publishedVersionId}，不看本枚举。
 *
 * <p>仅 {@link #ACTIVE} 允许业务按钮启动。{@link #PUBLISHED} 只兼容旧数据，不再表示可运行。
 */
public enum WorkflowDefinitionStatus {
  /** 草稿（尚未启用）。 */
  DRAFT("draft"),
  /** 运行中 / 已启用。 */
  ACTIVE("active"),
  /** 已停用。 */
  INACTIVE("inactive"),
  /** 已归档。 */
  ARCHIVED("archived"),
  /** 历史值：旧发布逻辑写入。不再表示可运行，发布时会收成 {@link #INACTIVE}。 */
  PUBLISHED("published");

  private final String value;

  WorkflowDefinitionStatus(String value) {
    this.value = value;
  }

  /** @return 持久化小写词 */
  public String getValue() {
    return value;
  }

  /**
   * 解析定义状态；无法识别时回落 {@link #DRAFT}。
   *
   * @param raw 原始字符串
   * @return 枚举
   */
  public static WorkflowDefinitionStatus from(String raw) {
    if (StrUtil.isBlank(raw)) {
      return DRAFT;
    }
    String key = raw.trim().toLowerCase(Locale.ROOT);
    for (WorkflowDefinitionStatus status : values()) {
      if (status.value.equals(key)) {
        return status;
      }
    }
    return DRAFT;
  }

  /**
   * 是否允许业务功能启动。只有启用态可以。
   *
   * @return 可启动为 true
   */
  public boolean isRunnable() {
    return this == ACTIVE;
  }

  /**
   * 发布新版本后应保留的启用开关。已启用、已停用、已归档保持原样；草稿和历史 published 收成停用。
   *
   * @param current 发布前状态
   * @return 发布后状态
   */
  public static WorkflowDefinitionStatus afterPublish(WorkflowDefinitionStatus current) {
    if (current == ACTIVE || current == INACTIVE || current == ARCHIVED) {
      return current;
    }
    return INACTIVE;
  }
}
