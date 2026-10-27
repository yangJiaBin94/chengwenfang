package com.chengwenfang.ai.facade.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * AI 连通性测试请求：使用前端表单草稿，不要求先保存。
 * <p>{@code config.apiKey} 空白时回落库中已保存密钥。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiConnectionTestRequest {
  /** 页面上当前填写的单侧配置。 */
  private ProviderConfigPatch config;
}
