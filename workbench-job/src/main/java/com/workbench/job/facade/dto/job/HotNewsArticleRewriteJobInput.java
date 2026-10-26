package com.workbench.job.facade.dto.job;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * {@link com.workbench.job.facade.constant.JobTypes#HOT_NEWS_ARTICLE_REWRITE} 入参。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HotNewsArticleRewriteJobInput {
  /** 文章 ID。 */
  private String articleId;
  /** 改写指令。 */
  private String instruction;
}
