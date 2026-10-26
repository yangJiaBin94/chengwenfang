package com.chengwenfang.job.facade.dto.job;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * {@link com.chengwenfang.job.facade.constant.JobTypes#HOT_NEWS_TOPIC_RESEARCH} 入参。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HotNewsTopicResearchJobInput {
  /** 选题 ID。 */
  private String topicId;
  /** 调研运行 ID。 */
  private String researchRunId;
}
