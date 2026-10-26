package com.workbench.mode.hotnews.domain;

import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 资料池素材评分明细（分维 + 使用建议）；持久化为条目列 + dimensions 子表。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResearchItemScoreDetails {
  /** 量表版本。 */
  private Integer rubricVersion;
  /** 五维打分结果。 */
  @Builder.Default
  private List<TopicDimensionScore> dimensions = new ArrayList<>();
  /** 使用建议：priority / supplement / cautious。 */
  private String recommendUse;
  /** 风险提示（可空）。 */
  private String riskNote;
}
