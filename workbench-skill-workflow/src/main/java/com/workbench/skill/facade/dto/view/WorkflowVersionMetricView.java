package com.workbench.skill.facade.dto.view;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 按版本的运行指标。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowVersionMetricView {
  /** 版本号标签。 */
  private String version;
  /** 运行次数。 */
  private Integer runs;
  /** 成功率 0–1。 */
  private Double successRate;
  /** P95 耗时（秒）。 */
  private Long p95DurationSeconds;
  /** Token 总量。 */
  private Long totalTokens;
}
