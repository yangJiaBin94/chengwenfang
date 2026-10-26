package com.workbench.skill.facade.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 更新工作流元数据请求。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowUpdateRequest {
  /** 名称。 */
  private String name;
  /** 描述。 */
  private String description;
  /** 状态。 */
  private String status;
}
