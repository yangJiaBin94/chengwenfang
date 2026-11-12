package com.chengwenfang.knowledge.infrastructure.vector;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 写入向量库的一条知识切块。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeVectorPoint {
  /** Qdrant 点 ID。 */
  private String id;
  /** 所属文档 ID。 */
  private String documentId;
  /** 来源类型。 */
  private String sourceKind;
  /** 来源引用 ID。 */
  private String sourceRefId;
  /** 媒体类型。 */
  private String mediaKind;
  /** 标题。 */
  private String title;
  /** 标题路径（Markdown 层级）。 */
  private String headingPath;
  /** 块序号。 */
  private Integer chunkIndex;
  /** 内容哈希。 */
  private String contentHash;
  /** 主题 ID。 */
  private String themeId;
  /** 前端深链接。 */
  private String deepLink;
  /** 可检索正文。 */
  private String text;
  /** 嵌入向量。 */
  private float[] embedding;
}
