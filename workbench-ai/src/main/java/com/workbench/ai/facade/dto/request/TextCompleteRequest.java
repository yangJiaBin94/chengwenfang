package com.workbench.ai.facade.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 文本补全请求。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TextCompleteRequest {
  /** 用户提示词。 */
  @NotBlank(message = "不能为空")
  private String prompt;
  /** 审计场景名。 */
  private String scene;
  /** 系统提示词；为空时只发送用户消息。 */
  private String systemPrompt;
  /** 可选超时秒数；为空则使用配置文件默认文本超时。 */
  private Integer timeoutSeconds;

  /** 兼容只传用户提示词与场景的调用方。 */
  public TextCompleteRequest(String prompt, String scene) {
    this.prompt = prompt;
    this.scene = scene;
  }
}
