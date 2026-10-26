package com.workbench.skill.facade.dto.view;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 校验问题项。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowValidationIssueView {
  /** 错误码。 */
  private String code;
  /** 消息。 */
  private String message;
  /** 相关节点 key。 */
  private String nodeKey;
}
