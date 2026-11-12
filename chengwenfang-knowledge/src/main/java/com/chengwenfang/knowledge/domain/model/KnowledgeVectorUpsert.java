package com.chengwenfang.knowledge.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 向量 upsert 载荷。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeVectorUpsert {
  /** 切块 ID（兼 Qdrant 点 ID）。 */
  private String chunkId;
  /** 所属文档 ID。 */
  private String documentId;
  /** 嵌入向量。 */
  private float[] vector;
}
