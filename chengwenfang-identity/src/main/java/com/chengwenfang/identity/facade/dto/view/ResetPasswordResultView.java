package com.chengwenfang.identity.facade.dto.view;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 管理员重置密码结果（明文仅返回一次）。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "重置密码结果")
public class ResetPasswordResultView {
  /** 用户 ID。 */
  @Schema(description = "用户 ID")
  private String userId;
  /** 登录名。 */
  @Schema(description = "登录名")
  private String username;
  /** 新明文密码，仅本次响应可见。 */
  @Schema(description = "新明文密码")
  private String password;
}
