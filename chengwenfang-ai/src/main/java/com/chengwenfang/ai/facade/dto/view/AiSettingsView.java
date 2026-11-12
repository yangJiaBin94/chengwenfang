package com.chengwenfang.ai.facade.dto.view;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** image + vision + embedding 公开配置。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiSettingsView {
  /** 图像生成模型公开配置。 */
  private ProviderPublicView image;
  /** 多模态补全模型公开配置。 */
  private ProviderPublicView vision;
  /** 文本嵌入模型公开配置。 */
  private ProviderPublicView embedding;
}
