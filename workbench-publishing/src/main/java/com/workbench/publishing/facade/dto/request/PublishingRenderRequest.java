package com.workbench.publishing.facade.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Markdown 渲染请求。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PublishingRenderRequest {
  /** Markdown 正文。 */
  @NotBlank(message = "不能为空")
  private String markdown;
  /** 主题版本 ID。 */
  private String themeVersionId;
  /** 主题 key（兼容旧调用）。 */
  private String themeKey;
}