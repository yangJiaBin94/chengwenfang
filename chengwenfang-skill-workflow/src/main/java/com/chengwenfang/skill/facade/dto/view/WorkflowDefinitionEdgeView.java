package com.chengwenfang.skill.facade.dto.view;

import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 工作流定义中的边。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowDefinitionEdgeView {
  /** 边 ID。 */
  private String id;
  /** 源节点 key。 */
  private String source;
  /** 源端口。 */
  private String sourcePort;
  /** 目标节点 key。 */
  private String target;
  /** 目标端口。 */
  private String targetPort;
  /** 条件表达式（可选）。 */
  private Map<String, Object> condition;
  /** 是否循环边。 */
  private Boolean loop;
  /** 最大迭代次数。 */
  private Integer maxIterations;
}
