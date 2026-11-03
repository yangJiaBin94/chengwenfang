package com.chengwenfang.content.facade.dto.view;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 热点主题采集策略 AI 建议结果。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HotNewsCollectStrategySuggestView {
  /** 推荐策略。 */
  private ThemeCollectStrategyView strategy;
  /** 是否来自 AI。 */
  private boolean fromAi;
}
