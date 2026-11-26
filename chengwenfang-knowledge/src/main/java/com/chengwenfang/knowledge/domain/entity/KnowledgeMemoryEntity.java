package com.chengwenfang.knowledge.domain.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 知识库长期记忆（表 {@code knowledge_memories}）。 */
@TableName("knowledge_memories")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeMemoryEntity {
  /** 主键。 */
  @TableId("id")
  private String id;
  /** 所属用户 ID。 */
  @TableField("owner_user_id")
  private String ownerUserId;
  /** 作用域。 */
  @TableField("scope_key")
  private String scopeKey;
  /** 记忆正文。 */
  @TableField("content")
  private String content;
  /** 嵌入向量 JSON。 */
  @TableField("embedding_json")
  private String embeddingJson;
  /** 出处。 */
  @TableField("provenance")
  private String provenance;
  /** 是否暂停召回。 */
  @TableField("paused")
  private Boolean paused;
  /** 过期时间。 */
  @TableField("expires_at")
  private LocalDateTime expiresAt;
  /** 创建时间。 */
  @TableField("created_at")
  private LocalDateTime createdAt;
}
