package com.workbench.content.facade.dto.view;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 热点主题评分量表 AI 生成结果。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HotNewsScoringRubricSuggestView {
  /** 生成的量表。 */
  private ScoringRubricView rubric;
  /** 是否来自 AI（false 表示规则回退）。 */
  private boolean fromAi;
}
