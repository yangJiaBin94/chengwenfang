package com.workbench.skill.facade.dto.view;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 工作流草稿视图。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowDraftView {
  /** 草稿 ID。 */
  private String id;
  /** 工作流 ID。 */
  private String workflowId;
  /** 基于的版本 ID。 */
  private String baseVersionId;
  /** 修订号。 */
  private Integer revision;
  /** 定义数据。 */
  private WorkflowDefinitionDataView definition;
  /** 定义数据（兼容旧字段）。 */
  private WorkflowDefinitionDataView definitionJson;
  /** 校验结果。 */
  private WorkflowValidationView validation;
  /** 更新时间（ISO-8601）。 */
  private String updatedAt;
}
