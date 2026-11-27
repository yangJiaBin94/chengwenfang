package com.chengwenfang.identity.facade.dto.view;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 当前登录用户视图。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "当前用户")
public class CurrentUserView {
  /** 用户 ID。 */
  @Schema(description = "用户 ID")
  private String id;
  /** 登录名。 */
  @Schema(description = "登录名")
  private String username;
  /** 展示名。 */
  @Schema(description = "展示名")
  private String displayName;
  /** 头像下载 URL，无头像时为 null。 */
  @Schema(description = "头像 URL")
  private String avatarUrl;
  /** 角色编码列表。 */
  @Builder.Default
  @Schema(description = "角色编码")
  private List<String> roles = new ArrayList<>();
  /** 权限编码列表。 */
  @Builder.Default
  @Schema(description = "权限编码")
  private List<String> permissions = new ArrayList<>();
}
