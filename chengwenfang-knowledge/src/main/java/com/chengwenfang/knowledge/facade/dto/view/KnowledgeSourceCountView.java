package com.chengwenfang.knowledge.facade.dto.view;

import com.chengwenfang.knowledge.facade.enums.KnowledgeSourceKind;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 按来源种类汇总的文档数量。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeSourceCountView {
  /** 来源种类。 */
  private KnowledgeSourceKind sourceKind;
  /** 文档数量。 */
  private Long count;
}
