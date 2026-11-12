package com.chengwenfang.prompt.facade.dto.view;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 工作流节点上的 提示词包绑定。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowPromptPackBindingView {
  /** 提示词包 ID。 */
  private String promptPackId;
  /** 提示词包名称。 */
  private String name;
  /** 优先级。 */
  private Integer priority;
  /** 是否启用。 */
  private Boolean enabled;
}
