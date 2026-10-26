package com.chengwenfang.job.facade.dto.job;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * {@link com.chengwenfang.job.facade.constant.JobTypes#HOT_ARTICLE_REVIEW_REPAIR} 出参。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HotArticleReviewRepairJobOutput {
  /** 候选稿 ID。 */
  private String candidateId;
  /** 状态。 */
  private String status;
  /** 文章 ID。 */
  private String articleId;
  /** 审核运行 ID。 */
  private String reviewId;
  /** 修复正文。 */
  private String content;
}
