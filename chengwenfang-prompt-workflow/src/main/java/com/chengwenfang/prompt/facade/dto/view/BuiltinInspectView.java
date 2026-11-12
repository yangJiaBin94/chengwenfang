package com.chengwenfang.prompt.facade.dto.view;

import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 内置提示词包 / 工作流配置体检结果（门面视图）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BuiltinInspectView {
  /** 是否全部通过（issues 为空）。 */
  private boolean ready;

  /** 缺失或不可用项描述列表；空表示通过。 */
  @Builder.Default
  private List<String> issues = new ArrayList<>();

  /** 首次部署补种脚本提示。 */
  private String bootstrapHint;

  /** 库内 提示词包 条数。 */
  private int promptPackCount;

  /** 期望的内置提示词包 数量。 */
  private int expectedPromptPackCount;

  /** 三条主工作流是否均无工作流域问题。 */
  private boolean workflowReady;
}
