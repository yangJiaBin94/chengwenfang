package com.workbench.skill.facade.dto.view;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 失败节点计数。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowFailedNodeMetricView {
  /** 节点 key。 */
  private String nodeKey;
  /** 失败次数。 */
  private Long count;
}
