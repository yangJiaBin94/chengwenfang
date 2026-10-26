package com.workbench.skill.facade.dto.view;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 草稿相对已发布版本的差异。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowDiffView {
  /** 变更列表。 */
  private List<Object> changes;
}
