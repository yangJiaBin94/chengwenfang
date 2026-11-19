package com.chengwenfang.knowledge.domain.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 知识库文档索引快照（表 {@code knowledge_documents}）。 */
@TableName("knowledge_documents")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeDocumentEntity {
  /** 主键。 */
  @TableId("id")
  private String id;
  /** 来源种类。 */
  @TableField("source_kind")
  private String sourceKind;
  /** 来源侧主键。 */
  @TableField("source_ref_id")
  private String sourceRefId;
  /** 介质种类。 */
  @TableField("media_kind")
  private String mediaKind;
  /** 标题。 */
  @TableField("title")
  private String title;
  /** 主题 ID。 */
  @TableField("theme_id")
  private String themeId;
  /** 原始附件文件 ID。 */
  @TableField("origin_file_id")
  private String originFileId;
  /** 归一化文本快照文件 ID。 */
  @TableField("text_file_id")
  private String textFileId;
  /** 可检索文本哈希。 */
  @TableField("content_hash")
  private String contentHash;
  /** 切块策略版本（与 {@code KnowledgeChunkStrategyVersions.CURRENT} 对齐才可 skip）。 */
  @TableField("chunk_strategy_version")
  private String chunkStrategyVersion;
  /** 来源版本戳。 */
  @TableField("source_version_stamp")
  private String sourceVersionStamp;
  /** 索引状态。 */
  @TableField("status")
  private String status;
  /** 切块数量。 */
  @TableField("chunk_count")
  private Integer chunkCount;
  /** 失败原因。 */
  @TableField("error_message")
  private String errorMessage;
  /** 前端深链。 */
  @TableField("deep_link")
  private String deepLink;
  /** 扩展标签等调试快照 JSON。 */
  @TableField("payload_json")
  private String payloadJson;
  /** 创建时间。 */
  @TableField("created_at")
  private LocalDateTime createdAt;
  /** 更新时间。 */
  @TableField("updated_at")
  private LocalDateTime updatedAt;
}
