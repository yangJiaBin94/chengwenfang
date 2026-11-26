package com.chengwenfang.identity.facade.enums;

import cn.hutool.core.util.StrUtil;
import java.util.Arrays;
import java.util.Optional;

/**
 * 用户角色闭集。
 */
public enum UserRole {
  /** 管理员：用户管理与跨用户数据范围。 */
  ADMIN("ADMIN"),
  /** 编辑：读写自有资源。 */
  EDITOR("EDITOR"),
  /** 只读：仅查看自有资源。 */
  VIEWER("VIEWER");

  private final String value;

  UserRole(String value) {
    this.value = value;
  }

  /**
   * @return 持久化 / Token 侧角色编码
   */
  public String getValue() {
    return value;
  }

  /**
   * 是否管理员。
   *
   * @param roleCode 角色编码
   * @return 匹配 ADMIN 时为 true
   */
  public static boolean isAdmin(String roleCode) {
    return ADMIN.matches(roleCode);
  }

  /**
   * 是否与给定编码匹配。
   *
   * @param roleCode 角色编码
   * @return 匹配时为 true
   */
  public boolean matches(String roleCode) {
    return value.equalsIgnoreCase(StrUtil.trim(roleCode));
  }

  /**
   * 解析角色编码。
   *
   * @param roleCode 编码
   * @return 枚举；未知则 empty
   */
  public static Optional<UserRole> from(String roleCode) {
    if (StrUtil.isBlank(roleCode)) {
      return Optional.empty();
    }
    return Arrays.stream(values()).filter(role -> role.matches(roleCode)).findFirst();
  }
}
