package com.chengwenfang.job.facade.dto.job;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * {@link com.chengwenfang.job.facade.constant.JobTypes#HOT_NEWS_VIDEO_PARSE} 入参。
 * <p>itemIds / fromStep 可空（未设置时序列化省略，与条件 put 一致）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HotNewsVideoParseJobInput {
  /** 选题 ID。 */
  private String topicId;
  /** 指定条目 ID；空或 null 表示全量候选。 */
  private List<String> itemIds;
  /** 起始解析步骤。 */
  private String fromStep;
}
