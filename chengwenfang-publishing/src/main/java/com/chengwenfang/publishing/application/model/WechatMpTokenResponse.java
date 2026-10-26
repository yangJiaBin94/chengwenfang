package com.chengwenfang.publishing.application.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 微信稳定版 access_token 接口响应。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WechatMpTokenResponse {
  /** access_token。 */
  private String accessToken;
  /** 有效期秒数。 */
  private int expiresInSeconds;
}
