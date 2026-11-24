package com.chengwenfang.app.bootstrap;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 系统就绪体检：内置提示词包 / 工作流，以及 Qdrant、密钥、热点加装与可选开关。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "系统就绪视图")
public class BuiltinReadyView {
  /** 默认档（课程 + 知识库）是否通过。热点未加装与 Graph/Agent 关闭不计入。 */
  @Schema(description = "课程 + 知识库是否就绪")
  private boolean ready;

  /** 缺失或不可用项描述列表；空表示通过。 */
  @Schema(description = "问题列表")
  @Builder.Default
  private List<String> issues = new ArrayList<>();

  /** 首次部署补种脚本提示。 */
  @Schema(description = "Bootstrap 脚本提示")
  private String bootstrapHint;

  /** 库内 提示词包 条数。 */
  @Schema(description = "库内 提示词包 数量")
  private int promptPackCount;

  /** 期望的内置提示词包 数量。 */
  @Schema(description = "期望内置提示词包 数量")
  private int expectedPromptPackCount;

  /** 三条主工作流是否均无工作流域问题（按 BuiltinIssueKind，非文案匹配）。 */
  @Schema(description = "内置工作流是否就绪")
  private boolean workflowReady;

  /** 面板检查项；与 {@link #issues} 同一接口，不另做清单。 */
  @Schema(description = "检查项")
  @Builder.Default
  private List<SystemReadinessItem> checks = new ArrayList<>();
}
