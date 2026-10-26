package com.workbench.app.bootstrap;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 内置 Skill / 工作流系统就绪体检结果。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "内置配置就绪视图")
public class BuiltinReadyView {
  /** 是否全部通过（issues 为空）。 */
  @Schema(description = "是否就绪")
  private boolean ready;

  /** 缺失或不可用项描述列表；空表示通过。 */
  @Schema(description = "问题列表")
  @Builder.Default
  private List<String> issues = new ArrayList<>();

  /** 首次部署补种脚本提示。 */
  @Schema(description = "Bootstrap 脚本提示")
  private String bootstrapHint;

  /** 库内 Skill 条数。 */
  @Schema(description = "库内 Skill 数量")
  private int skillCount;

  /** 期望的内置 Skill 数量。 */
  @Schema(description = "期望内置 Skill 数量")
  private int expectedSkillCount;

  /** 三条主工作流是否均无工作流域问题（按 BuiltinIssueKind，非文案匹配）。 */
  @Schema(description = "内置工作流是否就绪")
  private boolean workflowReady;
}
