package com.chengwenfang.identity.facade.constant;

/**
 * 权限编码开集常量（与 permissions 表种子对齐）。
 */
public final class AuthPermissions {
  /** 修改系统设置（含 AI / 渠道密钥）。 */
  public static final String SETTINGS_WRITE = "settings:write";
  /** 管理用户与角色。 */
  public static final String USER_MANAGE = "user:manage";
  /** 读写作品等内容。 */
  public static final String WORK_WRITE = "work:write";
  /** 读取作品等内容。 */
  public static final String WORK_READ = "work:read";

  private AuthPermissions() {}
}
