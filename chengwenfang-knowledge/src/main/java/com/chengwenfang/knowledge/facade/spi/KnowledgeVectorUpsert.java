package com.chengwenfang.knowledge.facade.spi;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 向量 upsert 载荷。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeVectorUpsert {
  /** 切块 ID（兼 Qdrant 点 ID）。 */
  private String chunkId;
  /** 所属文档 ID。 */
  private String documentId;
  /** 嵌入向量。 */
  private float[] vector;
  /** 来源类型。 */
  private String sourceKind;
  /** 来源业务 ID。 */
  private String sourceRefId;
  /** 介质类型。 */
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
  /** 深链。 */
  private String deepLink;
  /** 索引版本。 */
  private String indexVersion;
  /** 嵌入模型名。 */
  private String embeddingModel;
  /** 块正文（写入 payload）。 */
  private String content;
}
