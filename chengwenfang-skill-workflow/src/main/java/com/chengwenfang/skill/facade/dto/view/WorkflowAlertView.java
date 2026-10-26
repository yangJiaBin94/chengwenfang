package com.chengwenfang.skill.facade.dto.view;

import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 工作流告警视图。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowAlertView {
  /** 告警 ID。 */
  private String id;
  /** 工作流 ID。 */
  private String workflowId;
  /** 关联运行 ID。 */
  private String runId;
  /** 指标名。 */
  private String metric;
  /** 严重级别。 */
  private String severity;
  /** 标题。 */
  private String title;
  /** 消息。 */
  private String message;
  /** 阈值。 */
  private Map<String, Object> threshold;
  /** 状态。 */
  private String status;
  /** 创建时间。 */
  private String createdAt;
  /** 解决时间。 */
  private String resolvedAt;
}
