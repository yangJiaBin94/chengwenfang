package com.chengwenfang.knowledge.facade.dto.request;

import com.chengwenfang.knowledge.facade.enums.KnowledgeSourceKind;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 触发知识库全量或按来源重建索引。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeReindexRequest {
  /** 仅重建该来源；可空表示全部连接器。 */
  private KnowledgeSourceKind sourceKind;
}
