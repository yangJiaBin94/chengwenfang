package com.chengwenfang.identity.facade.dto.view;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 用户管理列表项。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "用户列表项")
public class UserListItemView {
  /** 用户 ID。 */
  @Schema(description = "用户 ID")
  private String id;
  /** 登录名。 */
  @Schema(description = "登录名")
  private String username;
  /** 展示名。 */
  @Schema(description = "展示名")
  private String displayName;
  /** 头像 URL，可空。 */
  @Schema(description = "头像 URL")
  private String avatarUrl;
  /** 是否启用。 */
  @Schema(description = "是否启用")
  private Boolean enabled;
  /** 角色编码列表。 */
  @Builder.Default
  @Schema(description = "角色编码")
  private List<String> roles = new ArrayList<>();
  /** 最近登录时间，可空。 */
  @Schema(description = "最近登录时间")
  private LocalDateTime lastLoginAt;
  /** 最近登录 IP，可空。 */
  @Schema(description = "最近登录 IP")
  private String lastLoginIp;
  /** 最近登录属地（市级），可空。 */
  @Schema(description = "最近登录属地（市级）")
  private String lastLoginRegion;
}
