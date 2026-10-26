package com.chengwenfang.ai.facade.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 更新请求：各侧可选，null 表示本侧不改。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiSettingsUpdateRequest {
  /** 文本侧配置补丁。 */
  private ProviderConfigPatch text;
  /** 图像生成侧配置补丁。 */
  private ProviderConfigPatch image;
  /** 多模态看图侧配置补丁。 */
  private ProviderConfigPatch vision;
}
