package com.chengwenfang.job.facade.dto.job;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * {@link com.chengwenfang.job.facade.constant.JobTypes#KNOWLEDGE_INDEX_DOCUMENT} 入参。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeIndexDocumentJobInput {
  /** 知识库文档 ID。 */
  private String documentId;
}
