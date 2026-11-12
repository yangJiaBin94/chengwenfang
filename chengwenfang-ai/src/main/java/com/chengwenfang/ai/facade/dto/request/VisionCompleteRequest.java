package com.chengwenfang.ai.facade.dto.request;

import jakarta.validation.constraints.NotBlank;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 多模态补全请求：图片可选；无图时为纯文本补全。
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
  /** 图片列表；为空或 null 表示纯文本补全。 */
  private List<VisionImagePart> images;
  /** 可选超时秒数；为空则使用配置文件默认多模态超时。 */
  private Integer timeoutSeconds;

  /**
   * 兼容只传用户提示词与场景的调用方。
   *
   * @param prompt 用户提示词
   * @param scene  审计场景
   */
  public VisionCompleteRequest(String prompt, String scene) {
    this.prompt = prompt;
    this.scene = scene;
  }
}
