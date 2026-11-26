package com.chengwenfang.identity.domain.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 登录用户实体，对应 {@code users} 表。 */
@TableName("users")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserEntity {
  /** 主键。 */
  @TableId("id")
  private String id;
  /** 登录名。 */
  @TableField("username")
  private String username;
  /** 密码哈希。 */
  @TableField("password_hash")
  private String passwordHash;
  /** 展示名。 */
  @TableField("display_name")
  private String displayName;
  /** 是否启用。 */
  @TableField("enabled")
  private Boolean enabled;
  /** 创建时间。 */
  @TableField("created_at")
  private LocalDateTime createdAt;
  /** 更新时间。 */
  @TableField("updated_at")
  private LocalDateTime updatedAt;
}
