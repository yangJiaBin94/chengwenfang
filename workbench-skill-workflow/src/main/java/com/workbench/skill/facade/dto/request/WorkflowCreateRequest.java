package com.workbench.skill.facade.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 创建工作流请求。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowCreateRequest {
  /** 业务 key（可选）。 */
  private String key;
  /** 名称。 */
  private String name;
  /** 描述。 */
  private String description;
  /** 场景。 */
  private String scene;
  /** 模式 ID。 */
  private String modeId;
}
