package com.workbench.ai.facade.dto.view;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 图像生成结果。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImageGenerateResult {
  /** dryRun 成功时为 true。 */
  private Boolean ok;
  /** 是否为探测模式。 */
  private Boolean dryRun;
  /** 使用的图像模型。 */
  private String model;
  /** 可选 base64 图数据。 */
  private String b64Json;
  /** 可选图片 URL。 */
  private String url;
}
