package com.workbench.skill.facade.dto.view;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Skill 与工作流节点的绑定视图。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SkillNodeBindingView {
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
