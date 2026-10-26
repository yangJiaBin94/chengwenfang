package com.chengwenfang.skill.facade.dto.view;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Skill 被工作流节点引用的应用场景视图。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SkillApplicationView {
  /** 模式 ID。 */
  private String modeId;
  /** 模式名称。 */
  private String modeName;
  /** 工作流 ID。 */
  private String workflowId;
  /** 工作流名称。 */
  private String workflowName;
  /** 节点 key。 */
  private String nodeKey;
  /** 节点名称。 */
  private String nodeName;
  /** 绑定状态（如 published）。 */
  private String bindingState;
}
