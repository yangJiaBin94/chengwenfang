package com.chengwenfang.prompt.facade.dto.view;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 提示词包 与工作流节点的绑定视图。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PromptPackNodeBindingView {
  /** 绑定 ID。 */
  private String id;
  /** 工作流类型。 */
  private String workflowType;
  /** 节点 key。 */
  private String nodeKey;
  /** 优先级（越小越靠前）。 */
  private Integer priority;
  /** 是否启用。 */
  private Boolean enabled;
}
