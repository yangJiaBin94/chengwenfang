package com.workbench.ai.infrastructure.openai;

import cn.hutool.core.annotation.Alias;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** OpenAI 兼容 {@code /images/generations} 请求体。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OpenAiImageGenerationRequest {
  /** 图像模型名。 */
  private String model;
  /** 生图提示词。 */
  private String prompt;
  /** 生成张数；序列化为上游字段 {@code n}。 */
  @Alias("n")
  private Integer imageCount;
  /** 尺寸，如 1024x1024。 */
  private String size;
  /** 质量档位（如 low/medium/high）；上游支持时传入。 */
  private String quality;
}
