package com.chengwenfang.app.bootstrap;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 系统就绪面板中的一项检查。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "系统就绪检查项")
public class SystemReadinessItem {
  /** 稳定键。 */
  @Schema(description = "检查项键")
  private String key;

  /** 面板标题。 */
  @Schema(description = "标题")
  private String label;

  /** 分组。 */
  @Schema(description = "分组：default / hotnews / optional")
  private SystemReadinessSection section;

  /** 状态。 */
  @Schema(description = "状态：pass / fail / not_installed / optional_off")
  private SystemReadinessStatus status;

  /** 给使用者看的说明，不含密钥。 */
  @Schema(description = "说明")
  private String detail;

  /** 为 true 时未通过则整页「课程 + 知识库」不算就绪。 */
  @Schema(description = "是否计入默认档就绪")
  private boolean required;
}
