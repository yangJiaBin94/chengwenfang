package com.workbench.content.facade.dto.view;

import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 主题级写作价值评分量表（建主题时生成并固化）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScoringRubricView {
  /** 量表版本号；重生成时递增。 */
  private Integer version;
  /** 生成时间（ISO 或可读字符串）。 */
  private String generatedAt;
  /** 维度列表（3～6）。 */
  @Builder.Default
  private List<ScoringRubricDimensionView> dimensions = new ArrayList<>();
  /** 整体打分补充指引（可空）。 */
  private String scoreGuidance;
}
