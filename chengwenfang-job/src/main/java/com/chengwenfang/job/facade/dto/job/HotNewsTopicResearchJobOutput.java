package com.chengwenfang.job.facade.dto.job;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * {@link com.chengwenfang.job.facade.constant.JobTypes#HOT_NEWS_TOPIC_RESEARCH} 出参。
 * <p>字段顺序与 {@code HotNewsResearchCompleteView} / beanToMap 一致。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HotNewsTopicResearchJobOutput {
  /** 调研运行 ID。 */
  private String researchRunId;
  /** 选题 ID。 */
  private String topicId;
  /** 材料条目数。 */
  private Integer itemCount;
  /** 冲突数。 */
  private Integer conflictCount;
  /** 状态。 */
  private String status;
  /** 简报 ID。 */
  private String briefId;
}
