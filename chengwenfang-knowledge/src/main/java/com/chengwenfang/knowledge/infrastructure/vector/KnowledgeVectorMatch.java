package com.chengwenfang.knowledge.infrastructure.vector;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 向量检索命中结果。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeVectorMatch {
  /** 相似度得分（余弦，越大越相似）。 */
  private double score;
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
  /** 标题路径。 */
  private String headingPath;
  /** 块序号。 */
  private Integer chunkIndex;
  /** 内容哈希。 */
  private String contentHash;
  /** 主题 ID。 */
  private String themeId;
  /** 深链接。 */
  private String deepLink;
  /** 正文摘录。 */
  private String text;
}
