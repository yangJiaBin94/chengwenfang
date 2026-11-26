package com.chengwenfang.identity.domain.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 用户-角色关联，对应 {@code user_roles}。 */
@TableName("user_roles")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserRoleEntity {
  /** 用户 ID。 */
  @TableField("user_id")
  private String userId;
  /** 角色编码。 */
  @TableField("role_code")
  private String roleCode;
}
