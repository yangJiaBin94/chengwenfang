package com.chengwenfang.mode.hotnews.facade.dto.view;

import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 热点原稿优化诊断结果。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HotNewsOptimizeCritiqueView {
  /** 文章 ID。 */
  private String articleId;
  /** 综合分 0～100。 */
  private Integer overallScore;
  /** 分项分。 */
  private Map<String, Integer> dimensionScores;
  /** 问题列表。 */
  private List<HotNewsOptimizeCritiqueIssueView> issues;
  /** 可编辑的完整优化建议。 */
  private String editableAdvice;
}
