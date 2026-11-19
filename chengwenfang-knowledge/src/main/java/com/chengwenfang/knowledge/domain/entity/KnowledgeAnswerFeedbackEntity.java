package com.chengwenfang.knowledge.domain.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 知识库答案赞踩反馈。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("knowledge_answer_feedback")
public class KnowledgeAnswerFeedbackEntity {
  /** 主键。 */
  @TableId
  private String id;
  /** 助手消息 ID。 */
  private String messageId;
  /** 会话 ID。 */
  private String sessionId;
  /** 评级 up/down。 */
  private String rating;
  /** 备注（踩时难例说明）。 */
  private String note;
  /** 创建时间。 */
  private LocalDateTime createdAt;
}
