package com.workbench.skill.facade.dto.view;

import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 工作流运行记录视图。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowRunView {
  /** 运行 ID。 */
  private String id;
  /** 关联 Job ID。 */
  private String jobId;
  /** 工作流名称。 */
  private String workflowName;
  /** 状态。 */
  private String status;
  /** 当前节点。 */
  private String currentNode;
  /** 工作流定义 ID。 */
  private String workflowDefinitionId;
  /** 工作流版本 ID。 */
  private String workflowVersionId;
  /** 版本号标签（可选）。 */
  private String version;
  /** 触发来源。 */
  private String triggerSource;
  /** 状态摘要。 */
  private Map<String, Object> stateSummary;
  /** 成本摘要。 */
  private Map<String, Object> costSummary;
  /** 创建时间。 */
  private String createdAt;
  /** 更新时间。 */
  private String updatedAt;
  /** 结束时间。 */
  private String finishedAt;
}
