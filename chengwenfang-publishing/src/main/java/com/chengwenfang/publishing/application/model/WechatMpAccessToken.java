package com.chengwenfang.publishing.application.model;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 内存缓存的公众号 access_token。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WechatMpAccessToken {
  /** access_token 字符串。 */
  private String accessToken;
  /** 过期时间（本地时钟）。 */
  private LocalDateTime expiresAt;
}
