package com.chengwenfang.identity.facade.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** 管理员启停用户请求。 */
@Data
@Schema(description = "设置用户启用状态")
public class SetUserEnabledRequest {
  /** 是否启用。 */
  @NotNull
  @Schema(description = "是否启用", requiredMode = Schema.RequiredMode.REQUIRED)
  private Boolean enabled;
}
