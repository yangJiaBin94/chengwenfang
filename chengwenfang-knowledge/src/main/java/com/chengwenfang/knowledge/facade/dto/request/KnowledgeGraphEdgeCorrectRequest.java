package com.chengwenfang.knowledge.facade.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 图边纠错请求。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeGraphEdgeCorrectRequest {
  /** Neo4j 边业务 UID。 */
  private String edgeUid;
  /** 来源切块。 */
  private String sourceChunkId;
  /** 置信度。 */
  private Double confidence;
  /** 说明。 */
  private String note;
  /** 显式启停；null 按置信度。 */
  private Boolean enabled;
  /** 是否按切块删边待重建。 */
  private Boolean rebuildChunk;
}
