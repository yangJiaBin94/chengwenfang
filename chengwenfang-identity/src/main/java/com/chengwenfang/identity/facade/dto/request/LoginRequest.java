package com.chengwenfang.identity.facade.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** 登录请求。 */
@Data
@Schema(description = "登录请求")
public class LoginRequest {
  /** 登录名。 */
  @NotBlank
  @Schema(description = "登录名", requiredMode = Schema.RequiredMode.REQUIRED)
  private String username;
  /** 明文密码。 */
  @NotBlank
  @Schema(description = "密码", requiredMode = Schema.RequiredMode.REQUIRED)
  private String password;
}
