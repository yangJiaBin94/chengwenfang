package com.workbench.skill.facade.dto.view;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 工作流节点上的 Skill 绑定。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowSkillBindingView {
  /** Skill ID。 */
  private String skillId;
  /** Skill 名称。 */
  private String name;
  /** 优先级。 */
  private Integer priority;
  /** 是否启用。 */
  private Boolean enabled;
}
