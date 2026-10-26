package com.chengwenfang.skill.facade.dto.view;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 可绑定的工作流节点目录项。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SkillWorkflowNodeView {
  /** 工作流类型。 */
  private String workflowType;
  /** 节点 key。 */
  private String nodeKey;
  /** 展示名称。 */
  private String name;
  /** 分组。 */
  private String group;
  /** 模型类型：text / image。 */
  private String modelType;
}
