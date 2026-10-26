package com.chengwenfang.skill.facade.dto.view;

import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 工作流定义中的节点。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowDefinitionNodeView {
  /** 节点 key。 */
  private String key;
  /** 节点类型 key。 */
  private String typeKey;
  /** 名称。 */
  private String name;
  /** 描述。 */
  private String description;
  /** 阶段。 */
  private String stage;
  /** 序号。 */
  private Integer sequenceNo;
  /** 是否启用。 */
  private Boolean enabled;
  /** 节点配置（含 timeoutSeconds 等）。 */
  private Map<String, Object> config;
  /** 画布位置。 */
  private WorkflowNodePositionView position;
  /** Skill 绑定列表。 */
  private List<WorkflowSkillBindingView> skillBindings;
}
