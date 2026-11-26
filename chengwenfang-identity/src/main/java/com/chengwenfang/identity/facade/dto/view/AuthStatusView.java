package com.chengwenfang.identity.facade.dto.view;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 鉴权公开状态（无需登录）。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "鉴权公开状态")
public class AuthStatusView {
  /** 是否开放自助注册。 */
  @Schema(description = "是否开放注册")
  private boolean registerEnabled;
}
