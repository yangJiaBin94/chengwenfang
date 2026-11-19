package com.chengwenfang.knowledge.facade.dto.view;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 答案反馈视图（含难例导出 JSON 草稿）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeAnswerFeedbackView {
  /** 反馈 ID。 */
  private String id;
  /** 消息 ID。 */
  private String messageId;
  /** 会话 ID。 */
  private String sessionId;
  /** 评级。 */
  private String rating;
  /** 备注。 */
  private String note;
  /** 创建时间。 */
  private LocalDateTime createdAt;
  /** 踩时生成的金标候选 JSON（人工审后进 rag-golden）。 */
  private String goldenCandidateJson;
}
