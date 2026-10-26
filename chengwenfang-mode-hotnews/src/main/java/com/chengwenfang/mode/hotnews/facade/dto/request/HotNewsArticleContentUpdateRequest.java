package com.chengwenfang.mode.hotnews.facade.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 双轨内容更新请求（对齐前端 saveHotNewsArticleContent）。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HotNewsArticleContentUpdateRequest {
  /** 内容 */
  private String content;
  /** 基准内容哈希 */
  private String baseContentHash;
  /** 原因 */
  private String reason;
}
