package com.workbench.ai.infrastructure.openai;

import cn.hutool.core.annotation.Alias;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** OpenAI 兼容 {@code /images/generations} 响应体。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OpenAiImageGenerationResponse {
  /** 图片结果列表；对应上游字段 {@code data}。 */
  @Alias("data")
  private List<ImageData> images;

  /** 单张图片结果。 */
  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class ImageData {
    /** Base64 编码的图片（无 data URL 前缀）。 */
    @Alias("b64_json")
    private String b64Json;
    /** 可选的图片 URL。 */
    private String url;
  }
}
