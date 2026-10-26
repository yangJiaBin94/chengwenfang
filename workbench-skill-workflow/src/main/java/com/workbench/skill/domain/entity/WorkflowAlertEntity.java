package com.workbench.skill.domain.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 工作流运行告警，对应 workflow_alerts。 */
@TableName("workflow_alerts")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowAlertEntity {
  /** 主键 ID。 */
  @TableId("id")
  private String id;
  /** 所属工作流。 */
  @TableField("workflow_id")
  private String workflowId;
  /** 触发告警的运行。 */
  @TableField("run_id")
  private String runId;
  /** 告警指标。 */
  @TableField("metric")
  private String metric;
  /** 严重级别。 */
  @TableField("severity")
  private String severity;
  /** 告警标题。 */
  @TableField("title")
  private String title;
  /** 告警详情。 */
  @TableField("message")
  private String message;
  /** open / resolved。 */
  @TableField("status")
  private String status;
  /** 创建时间。 */
  @TableField("created_at")
  private LocalDateTime createdAt;
  /** 解决时间。 */
  @TableField("resolved_at")
  private LocalDateTime resolvedAt;
}
