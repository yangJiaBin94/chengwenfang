package com.workbench.ai.facade.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 多模态看图补全请求。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VisionCompleteRequest {
  /** 用户提示词。 */
  @NotBlank(message = "不能为空")
  private String prompt;
  /** 系统提示词；为空时只发送用户消息。 */
  private String systemPrompt;
  /** 审计场景名。 */
  private String scene;
  /** 图片列表（至少一张）。 */
  @NotEmpty(message = "至少一张图片")
  private List<VisionImagePart> images;
  /** 可选超时秒数；为空则使用配置文件默认多模态超时。 */
  private Integer timeoutSeconds;
}
