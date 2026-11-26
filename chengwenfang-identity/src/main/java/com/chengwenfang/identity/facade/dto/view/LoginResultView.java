package com.chengwenfang.identity.facade.dto.view;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 登录成功结果。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "登录结果")
public class LoginResultView {
  /** 访问令牌。 */
  @Schema(description = "访问令牌")
  private String token;
  /** 令牌头名称（如 Authorization）。 */
  @Schema(description = "令牌头名称")
  private String tokenName;
  /** 当前用户摘要。 */
  @Schema(description = "当前用户")
  private CurrentUserView user;
}
