package com.workbench.content.facade.dto.view;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 主题写作价值评分量表中的单个维度。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScoringRubricDimensionView {
  /** 稳定英文键（snake_case），用于落库与匹配分维结果。 */
  private String key;
  /** 卡片展示用中文名。 */
  private String label;
  /** 打分释义 / 判定指引。 */
  private String description;
  /** 权重百分比（正整数）；全部维度之和须为 100。 */
  private Integer weight;
}
