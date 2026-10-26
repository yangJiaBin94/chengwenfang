package com.chengwenfang.skill.facade.dto.view;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 按节点的运行指标。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowNodeMetricView {
  /** 节点 key。 */
  private String nodeKey;
  /** 尝试次数。 */
  private Integer attempts;
  /** 失败次数。 */
  private Integer failures;
  /** 平均耗时（毫秒）。 */
  private Long averageDurationMs;
}
