package com.chengwenfang.knowledge.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 向量检索命中。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeVectorHit {
  /** 切块 ID。 */
  private String chunkId;
  /** 文档 ID。 */
  private String documentId;
  /** 相似度得分（越大越好）。 */
  private double score;
}
