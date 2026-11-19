package com.chengwenfang.knowledge.domain.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 知识库切块（表 {@code knowledge_chunks}）。 */
@TableName("knowledge_chunks")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeChunkEntity {
  /** 主键。 */
  @TableId("id")
  private String id;
  /** 文档 ID。 */
  @TableField("document_id")
  private String documentId;
  /** 父块 ID。 */
  @TableField("parent_chunk_id")
  private String parentChunkId;
  /** 块序号。 */
  @TableField("chunk_index")
  private Integer chunkIndex;
  /** 标题路径。 */
  @TableField("heading_path")
  private String headingPath;
  /** 原文起始偏移。 */
  @TableField("char_span_start")
  private Integer charSpanStart;
  /** 原文结束偏移。 */
  @TableField("char_span_end")
  private Integer charSpanEnd;
  /** 块正文。 */
  @TableField("content")
  private String content;
  /** 字数估计。 */
  @TableField("token_estimate")
  private Integer tokenEstimate;
  /** Qdrant 点 ID。 */
  @TableField("qdrant_point_id")
  private String qdrantPointId;
  /** 嵌入模型名。 */
  @TableField("embedding_model")
  private String embeddingModel;
  /** 索引版本。 */
  @TableField("index_version")
  private String indexVersion;
  /** 写入索引时间。 */
  @TableField("indexed_at")
  private LocalDateTime indexedAt;
  /** 创建时间。 */
  @TableField("created_at")
  private LocalDateTime createdAt;
}
