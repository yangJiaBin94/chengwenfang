package com.workbench.ai.facade.dto.view;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** text + image + vision 公开配置。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiSettingsView {
  /** 文本模型公开配置。 */
  private ProviderPublicView text;
  /** 图像生成模型公开配置。 */
  private ProviderPublicView image;
  /** 多模态看图模型公开配置。 */
  private ProviderPublicView vision;
}
