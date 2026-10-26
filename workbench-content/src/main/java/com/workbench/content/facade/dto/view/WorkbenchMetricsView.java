package com.workbench.content.facade.dto.view;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 工作台进度指标。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkbenchMetricsView {
  /** 课程总数。 */
  private int courseCount;

  /** 已审核数。 */
  private int reviewedCount;

  /** 待处理数。 */
  private int pendingCount;

  /** 配图数。 */
  private int imageCount;
}
