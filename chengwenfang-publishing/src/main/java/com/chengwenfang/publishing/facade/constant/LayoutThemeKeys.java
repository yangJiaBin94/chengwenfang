package com.chengwenfang.publishing.facade.constant;

/**
 * 排版主题类型 / 状态 / 默认主题标识（库列与前端字段）。
 */
public final class LayoutThemeKeys {
  private LayoutThemeKeys() {}

  /** 系统内置主题类型。 */
  public static final String TYPE_SYSTEM = "system";
  /** 用户自定义主题类型。 */
  public static final String TYPE_CUSTOM = "custom";

  /** 库内启用状态。 */
  public static final String DB_ACTIVE = "active";
  /** 前端启用状态。 */
  public static final String FE_ENABLED = "enabled";
  /** 停用状态（库与前端同值）。 */
  public static final String DISABLED = "disabled";

  /** 空库种子默认主题主键。 */
  public static final String DEFAULT_ID = "publishing-theme-default";
  /** 空库种子默认主题 key。 */
  public static final String DEFAULT_KEY = "default";
}
