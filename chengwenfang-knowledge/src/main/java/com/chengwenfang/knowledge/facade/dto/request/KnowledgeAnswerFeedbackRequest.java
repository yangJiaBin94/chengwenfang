package com.chengwenfang.knowledge.facade.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 答案赞踩请求。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeAnswerFeedbackRequest {
  /** 评级：up / down。 */
  private String rating;
  /** 备注。 */
  private String note;
}
