package com.chengwenfang.identity.facade.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 校验当前登录用户密码请求。 */
@Data
@Schema(description = "校验当前密码")
public class VerifyPasswordRequest {
  /** 当前密码（明文）。 */
  @NotBlank
  @Size(min = 1, max = 72)
  @Schema(description = "当前密码", requiredMode = Schema.RequiredMode.REQUIRED)
  private String currentPassword;
}
