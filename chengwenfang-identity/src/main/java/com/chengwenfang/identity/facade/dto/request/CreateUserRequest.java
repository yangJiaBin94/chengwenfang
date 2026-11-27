package com.chengwenfang.identity.facade.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 管理员创建用户请求。 */
@Data
@Schema(description = "创建用户")
public class CreateUserRequest {
  /** 登录名。 */
  @NotBlank
  @Size(min = 3, max = 64)
  @Schema(description = "登录名", requiredMode = Schema.RequiredMode.REQUIRED)
  private String username;
  /** 明文密码。 */
  @NotBlank
  @Size(min = 6, max = 72)
  @Schema(description = "密码", requiredMode = Schema.RequiredMode.REQUIRED)
  private String password;
  /** 展示名，可空则回退为用户名。 */
  @Size(max = 128)
  @Schema(description = "展示名")
  private String displayName;
  /** 角色编码：ADMIN / EDITOR / VIEWER。 */
  @NotBlank
  @Schema(description = "角色编码", requiredMode = Schema.RequiredMode.REQUIRED)
  private String role;
}
