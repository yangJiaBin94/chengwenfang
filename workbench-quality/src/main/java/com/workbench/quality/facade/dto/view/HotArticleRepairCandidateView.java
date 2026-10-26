package com.workbench.quality.facade.dto.view;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 热点修复候选视图。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HotArticleRepairCandidateView {
  /** 候选 ID */
  private String id;
  /** 审稿运行 ID */
  private String reviewRunId;
  /** 文章 ID */
  private String articleId;
  /** 任务 ID */
  private String jobId;
  /** 状态 */
  private String status;
  /** 候选正文 */
  private String content;
  /** 差异摘要 */
  private String diffSummary;
  /** 问题 ID JSON */
  private String issueIdsJson;
  /** 创建时间 */
  private LocalDateTime createdAt;
  /** 裁决时间 */
  private LocalDateTime decidedAt;
}
