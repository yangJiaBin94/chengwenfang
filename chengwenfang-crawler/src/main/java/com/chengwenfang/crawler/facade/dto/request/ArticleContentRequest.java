package com.chengwenfang.crawler.facade.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 文章正文解析请求。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ArticleContentRequest {
  /** 文章 URL。 */
  private String url;
  /** 搜索阶段摘要（兜底）。 */
  private String fallbackSummary;
  /** 候选标题（仅日志）。 */
  private String title;
}
