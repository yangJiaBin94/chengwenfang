package com.chengwenfang.knowledge.facade.dto.view;

import com.chengwenfang.knowledge.facade.enums.KnowledgeMediaKind;
import com.chengwenfang.knowledge.facade.enums.KnowledgeSourceKind;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** RAG 回答中的引用片段。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeCitationView {
  /** 文档 ID。 */
  private String documentId;
  /** 切块 ID。 */
  private String chunkId;
  /** 来源种类。 */
  private KnowledgeSourceKind sourceKind;
  /** 介质种类。 */
  private KnowledgeMediaKind mediaKind;
  /** 文档标题。 */
  private String title;
  /** 引用摘要。 */
  private String snippet;
  /** 深链。 */
  private String deepLink;
  /** 标题路径。 */
  private String headingPath;
  /** 原文起始偏移。 */
  private Integer charSpanStart;
  /** 原文结束偏移。 */
  private Integer charSpanEnd;
  /** 融合分。 */
  private Double score;
}
