package com.chengwenfang.job.facade.dto.job;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * {@link com.chengwenfang.job.facade.constant.JobTypes#KNOWLEDGE_RECONCILE} 入参（可空）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeReconcileJobInput {
  /** true 时只统计预览，不删库、不入队重建。 */
  private Boolean dryRun;
}
