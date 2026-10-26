package com.chengwenfang.skill.facade.dto.view;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 已发布版本视图。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowVersionView {
  /** 版本 ID。 */
  private String id;
  /** 工作流 ID。 */
  private String workflowId;
  /** 版本号标签。 */
  private String version;
  /** 修订号。 */
  private Integer revision;
  /** 快照定义。 */
  private WorkflowDefinitionDataView snapshot;
  /** 校验结果。 */
  private WorkflowValidationView validation;
  /** 发布说明。 */
  private String releaseNotes;
  /** 发布人。 */
  private String publishedBy;
  /** 创建时间（ISO-8601）。 */
  private String createdAt;
  /** 发布后回写的已发布版本 ID（兼容字段）。 */
  private String publishedVersionId;
}
