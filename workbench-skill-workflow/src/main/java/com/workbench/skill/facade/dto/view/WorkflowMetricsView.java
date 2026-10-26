package com.workbench.skill.facade.dto.view;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 工作流运行指标汇总。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowMetricsView {
  /** 运行总数（兼容字段）。 */
  private Integer runCount;
  /** 运行总数。 */
  private Integer totalRuns;
  /** 成功率 0–1。 */
  private Double successRate;
  /** 平均耗时（秒）。 */
  private Double averageDurationSeconds;
  /** P95 耗时（秒）。 */
  private Long p95DurationSeconds;
  /** 失败节点排行。 */
  private List<WorkflowFailedNodeMetricView> failedNodes;
}
