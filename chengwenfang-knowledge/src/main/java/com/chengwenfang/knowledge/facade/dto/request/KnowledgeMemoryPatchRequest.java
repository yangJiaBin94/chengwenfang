package com.chengwenfang.knowledge.facade.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 暂停或恢复知识库长期记忆。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeMemoryPatchRequest {
  /** 是否暂停召回。 */
  private Boolean paused;
}
