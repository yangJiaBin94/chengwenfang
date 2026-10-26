package com.chengwenfang.sourcing.facade.enums;

import cn.hutool.core.util.StrUtil;

/**
 * 搜索词 × 网站拉黑状态（闭集）。
 */
public enum QuerySiteBlockStatus {
  /** 生效中。 */
  ACTIVE("active"),
  /** 已撤销。 */
  REVOKED("revoked");

  private final String value;

  QuerySiteBlockStatus(String value) {
    this.value = value;
  }

  /**
   * @return 持久化字面量
   */
  public String getValue() {
    return value;
  }

  /**
   * 是否为生效状态。
   *
   * @param raw 原始字符串
   * @return true 表示 active
   */
  public static boolean isActive(String raw) {
    return ACTIVE.matches(raw);
  }

  /**
   * 是否与给定字面量匹配（忽略大小写）。
   *
   * @param raw 原始字符串
   * @return 是否匹配
   */
  public boolean matches(String raw) {
    return StrUtil.isNotBlank(raw) && value.equalsIgnoreCase(raw.trim());
  }

  /**
   * @param raw 原始字符串
   * @return 枚举；无法识别时返回 {@link #ACTIVE}
   */
  public static QuerySiteBlockStatus fromOrDefault(String raw) {
    if (StrUtil.isBlank(raw)) {
      return ACTIVE;
    }
    String normalized = raw.trim().toLowerCase();
    for (QuerySiteBlockStatus item : values()) {
      if (item.value.equals(normalized)) {
        return item;
      }
    }
    return ACTIVE;
  }
}
