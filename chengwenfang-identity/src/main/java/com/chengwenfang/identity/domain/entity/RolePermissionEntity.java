package com.chengwenfang.identity.domain.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 角色-权限关联，对应 {@code role_permissions}。 */
@TableName("role_permissions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RolePermissionEntity {
  /** 角色编码。 */
  @TableField("role_code")
  private String roleCode;
  /** 权限编码。 */
  @TableField("permission_code")
  private String permissionCode;
}
