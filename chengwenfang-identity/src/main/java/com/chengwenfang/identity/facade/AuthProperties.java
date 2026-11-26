package com.chengwenfang.identity.facade;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 鉴权相关配置：{@code chengwenfang.auth.*}（登录始终强制，无总开关）。
 */
@Data
@ConfigurationProperties(prefix = "chengwenfang.auth")
public class AuthProperties {
  /** 是否开放自助注册。 */
  private boolean registerEnabled = false;
}
