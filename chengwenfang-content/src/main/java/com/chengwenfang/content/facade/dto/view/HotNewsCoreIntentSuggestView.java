package com.chengwenfang.content.facade.dto.view;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 热点主题主要意图 AI 建议结果。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HotNewsCoreIntentSuggestView {
  /** 推导出的意图（confirmed 固定为 false，需用户确认）。 */
  private ThemeCoreIntentView intent;
  /** 是否来自 AI。 */
  private boolean fromAi;
}
