package com.workbench.skill.facade.dto.view;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 两版本差异。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowVersionDiffView {
  /** 起始版本。 */
  private String from;
  /** 目标版本。 */
  private String to;
  /** 变更列表。 */
  private List<Object> changes;
}
