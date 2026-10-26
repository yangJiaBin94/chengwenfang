package com.chengwenfang.skill.facade.dto.view;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 工作流定义校验结果。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowValidationView {
  /** 是否通过。 */
  private Boolean valid;
  /** 错误列表。 */
  private List<WorkflowValidationIssueView> errors;
  /** 警告列表。 */
  private List<WorkflowValidationIssueView> warnings;
  /** 校验时间（ISO-8601）。 */
  private String checkedAt;
  /** 定义哈希。 */
  private String definitionHash;
}
