package com.chengwenfang.job.facade.dto.job;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * {@link com.chengwenfang.job.facade.constant.JobTypes#ARTICLE_REPAIR} 桩出参（文章不存在时）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ArticleRepairStubJobOutput {
  /** 是否成功。 */
  private Boolean ok;
  /** 是否桩路径。 */
  private Boolean stub;
  /** 文章 ID。 */
  private String articleId;
  /** 文档 ID；可空省略。 */
  private String documentId;
}
