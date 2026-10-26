package com.workbench.ai.facade.dto.view;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 单侧 provider 对外视图（不含明文 apiKey）。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProviderPublicView {
  /** 配置 kind：text / image。 */
  private String kind;
  /** 显示名称。 */
  private String name;
  /** 模型名。 */
  private String model;
  /** 上游 Base URL。 */
  private String baseUrl;
  /** 协议，如 openai_chat。 */
  private String protocol;
  /** 超时秒数。 */
  private Integer timeoutSeconds;
  /** 最大重试次数。 */
  private Integer maxRetries;
  /** 采样温度。 */
  private Double temperature;
  /** 最大输出 token。 */
  private Integer maxOutputTokens;
  /** 图像质量参数。 */
  private String imageQuality;
  /** 是否启用。 */
  private Boolean enabled;
  /** 是否已配置 apiKey（不暴露明文）。 */
  private boolean apiKeyConfigured;
}
