package com.workbench.mode.hotnews.facade.dto.view;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 选题调研完成结果视图。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HotNewsResearchCompleteView {
  /** 调研运行 ID */
  private String researchRunId;
  /** 选题 ID */
  private String topicId;
  /** 材料条目数 */
  private Integer itemCount;
  /** 冲突数 */
  private Integer conflictCount;
  /** 状态 */
  private String status;
  /** 简报 ID */
  private String briefId;
}
