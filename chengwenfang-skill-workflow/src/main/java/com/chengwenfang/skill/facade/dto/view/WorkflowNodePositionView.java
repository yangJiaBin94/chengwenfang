package com.chengwenfang.skill.facade.dto.view;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 画布节点坐标。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowNodePositionView {
  /** X 坐标。 */
  private Double x;
  /** Y 坐标。 */
  private Double y;
}
