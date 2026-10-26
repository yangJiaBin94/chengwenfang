package com.workbench.app.adapter.web.common;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 健康检查响应。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HealthView {
  /** 服务名。 */
  private String service;
  /** 版本号。 */
  private String version;
  /** 运行环境。 */
  private String environment;
}
