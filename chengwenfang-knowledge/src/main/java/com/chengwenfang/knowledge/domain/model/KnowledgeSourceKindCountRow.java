package com.chengwenfang.knowledge.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 按来源类型聚合的文档计数行。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeSourceKindCountRow {
  /** 来源类型值。 */
  private String sourceKind;
  /** 文档数。 */
  private Long cnt;
}
