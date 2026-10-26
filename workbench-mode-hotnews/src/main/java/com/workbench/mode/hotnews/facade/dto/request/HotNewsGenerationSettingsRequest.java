package com.workbench.mode.hotnews.facade.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 生成参数更新请求。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HotNewsGenerationSettingsRequest {
  /** 统一文档 id（公众号轨）；空则忽略。 */
  private String documentId;
  /** 字数选项 */
  private String wordCountOption;
  /** 配图数量选项 */
  private String imageCountOption;
}
