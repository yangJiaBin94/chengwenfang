package com.chengwenfang.identity.facade.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 本人修改密码请求。
 */
@Data
@Schema(description = "修改密码请求")
public class ChangePasswordRequest {
  /** 当前密码（明文）。 */
  @NotBlank
  @Size(min = 1, max = 72)
  @Schema(description = "当前密码", requiredMode = Schema.RequiredMode.REQUIRED)
  private String currentPassword;

  /** 新密码（明文）。 */
  @NotBlank
  @Size(min = 6, max = 72)
  @Schema(description = "新密码", requiredMode = Schema.RequiredMode.REQUIRED)
  private String newPassword;
}
