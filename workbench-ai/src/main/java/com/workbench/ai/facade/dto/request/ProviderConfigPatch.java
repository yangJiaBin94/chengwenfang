package com.workbench.ai.facade.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 单侧配置补丁；字段为 null 表示不改；{@code apiKey} 空白亦不覆盖。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProviderConfigPatch {
  /** 显示名称。 */
  private String name;
  /** 模型名。 */
  private String model;
  /** 上游 Base URL。 */
  private String baseUrl;
  /** 协议。 */
  private String protocol;
  /** API Key；空白表示保留原值。 */
  private String apiKey;
  /** 超时秒数。 */
  private Integer timeoutSeconds;
  /** 图片质量档位（low/medium/high）；仅 image 侧有意义。 */
  private String imageQuality;
  /** 是否启用。 */
  private Boolean enabled;
}
