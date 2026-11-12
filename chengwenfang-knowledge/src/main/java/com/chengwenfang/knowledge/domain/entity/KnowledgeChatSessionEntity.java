package com.chengwenfang.knowledge.domain.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 知识库对话会话（表 {@code knowledge_chat_sessions}）。 */
@TableName("knowledge_chat_sessions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeChatSessionEntity {
  /** 主键。 */
  @TableId("id")
  private String id;
  /** 会话标题。 */
  @TableField("title")
  private String title;
  /** 创建时间。 */
  @TableField("created_at")
  private LocalDateTime createdAt;
  /** 更新时间。 */
  @TableField("updated_at")
  private LocalDateTime updatedAt;
}
