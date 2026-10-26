package com.chengwenfang.content.facade.dto.view;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 成文坊首页聚合数据。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChengwenfangDataView {
  /** 工作区信息。 */
  private ChengwenfangWorkspaceView workspace;

  /** 生成时间。 */
  private String generatedAt;

  /** 课程列表。 */
  private List<CourseView> courses;

  /** 进度指标。 */
  private ChengwenfangMetricsView metrics;
}
