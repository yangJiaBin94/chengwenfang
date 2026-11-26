package com.chengwenfang.identity.facade.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 注册请求（可配置关闭）。 */
@Data
@Schema(description = "注册请求")
public class RegisterRequest {
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
  /** 展示名，可空。 */
  @Size(max = 128)
  @Schema(description = "展示名")
  private String displayName;
}
