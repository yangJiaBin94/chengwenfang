package com.chengwenfang.mode.hotnews.facade.dto.view;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 调研冲突提示视图。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HotNewsResearchConflictView {
  /** 冲突 ID */
  private String id;
  /** 运行 ID */
  private String runId;
  /** 调研运行 ID（兼容） */
  private String researchRunId;
  /** 选题 ID */
  private String topicId;
  /** 主张 */
  private String claim;
  /** 详情 */
  private String detail;
  /** 创建时间 */
  private LocalDateTime createdAt;
}
