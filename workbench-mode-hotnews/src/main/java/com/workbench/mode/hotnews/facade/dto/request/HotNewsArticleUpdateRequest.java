package com.workbench.mode.hotnews.facade.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 更新公众号正文请求（对齐前端 saveHotNewsArticle）。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HotNewsArticleUpdateRequest {
  /** 正文 */
  private String content;
  /** 基准内容哈希 */
  private String baseContentHash;
  /** 原因 */
  private String reason;
}
