package com.chengwenfang.ai.facade.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 图像生成请求。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImageGenerateRequest {
  /** 生图提示词。 */
  private String prompt;
  /** 尺寸，如 1024x1024；null 时用默认。 */
  private String size;
  /** true 时只校验配置不调上游。 */
  private Boolean dryRun;
}
