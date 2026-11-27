package com.chengwenfang.identity.facade.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 当前用户更新资料请求。 */
@Data
@Schema(description = "更新资料")
public class UpdateProfileRequest {
  /** 展示名（昵称）。 */
  @NotBlank
  @Size(min = 1, max = 128)
  @Schema(description = "展示名", requiredMode = Schema.RequiredMode.REQUIRED)
  private String displayName;
}
