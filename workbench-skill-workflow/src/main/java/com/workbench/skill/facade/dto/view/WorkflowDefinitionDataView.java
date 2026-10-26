package com.workbench.skill.facade.dto.view;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 工作流图定义数据。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowDefinitionDataView {
  /** Schema 版本。 */
  private String schemaVersion;
  /** 名称。 */
  private String name;
  /** 描述。 */
  private String description;
  /** 场景。 */
  private String scene;
  /** 模式 ID。 */
  private String modeId;
  /** 节点列表。 */
  private List<WorkflowDefinitionNodeView> nodes;
  /** 边列表。 */
  private List<WorkflowDefinitionEdgeView> edges;
}
