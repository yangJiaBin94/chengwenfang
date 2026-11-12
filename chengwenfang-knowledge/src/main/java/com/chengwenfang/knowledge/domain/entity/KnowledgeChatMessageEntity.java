package com.chengwenfang.knowledge.domain.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 知识库对话消息（表 {@code knowledge_chat_messages}）。 */
@TableName("knowledge_chat_messages")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeChatMessageEntity {
  /** 主键。 */
  @TableId("id")
  private String id;
  /** 会话 ID。 */
  @TableField("session_id")
  private String sessionId;
  /** 角色：user / assistant。 */
  @TableField("role_key")
  private String roleKey;
  /** 消息正文。 */
  @TableField("content")
  private String content;
  /** 引用等扩展快照 JSON。 */
  @TableField("payload_json")
  private String payloadJson;
  /** 创建时间。 */
  @TableField("created_at")
  private LocalDateTime createdAt;
}
