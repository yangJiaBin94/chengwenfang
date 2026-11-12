package com.chengwenfang.knowledge.facade.dto.view;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 知识库索引统计。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeIndexStatsView {
  /** 文档总数。 */
  private Long total;
  /** 可检索文档数。 */
  private Long ready;
  /** 索引失败数。 */
  private Long failed;
  /** 按来源种类计数。 */
  private List<KnowledgeSourceCountView> bySourceKind;
}
