package com.chengwenfang.skill.facade.dto.view;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 节点上的 Skill 绑定（用于组合提示词）。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SkillPromptBindingView {
  /** Skill ID。 */
  private String skillId;
  /** 优先级（越小越靠前）。 */
  private Integer priority;
  /** 是否启用。 */
  private Boolean enabled;
}
