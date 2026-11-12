package com.chengwenfang.prompt.facade.dto.view;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 节点上的 提示词包绑定（用于组合提示词）。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PromptPackPromptBindingView {
  /** 提示词包 ID。 */
  private String promptPackId;
  /** 优先级（越小越靠前）。 */
  private Integer priority;
  /** 是否启用。 */
  private Boolean enabled;
}
