package com.chengwenfang.job.facade.dto.job;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * {@link com.chengwenfang.job.facade.constant.JobTypes#ARTICLE_REPAIR} 入参。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ArticleRepairJobInput {
  /** 文章 ID。 */
  private String articleId;
  /** 统一文档 ID。 */
  private String documentId;
  /** 课程 ID。 */
  private String courseId;
  /** 作品 ID（与 courseId 同值写入）。 */
  private String workId;
}
