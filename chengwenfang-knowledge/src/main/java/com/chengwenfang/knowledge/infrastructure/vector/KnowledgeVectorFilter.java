package com.chengwenfang.knowledge.infrastructure.vector;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 向量检索可选过滤条件（字段为空表示不限制）。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeVectorFilter {
  /** 限定文档 ID。 */
  private String documentId;
  /** 限定来源类型。 */
  private String sourceKind;
  /** 限定媒体类型。 */
  private String mediaKind;
  /** 限定主题 ID。 */
  private String themeId;
}
