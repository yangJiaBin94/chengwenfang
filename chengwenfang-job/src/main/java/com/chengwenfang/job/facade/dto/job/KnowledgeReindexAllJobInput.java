package com.chengwenfang.job.facade.dto.job;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * {@link com.chengwenfang.job.facade.constant.JobTypes#KNOWLEDGE_REINDEX_ALL} 入参。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeReindexAllJobInput {
  /** 可选：仅重建该来源（如 work_output）。 */
  private String sourceKind;
}
