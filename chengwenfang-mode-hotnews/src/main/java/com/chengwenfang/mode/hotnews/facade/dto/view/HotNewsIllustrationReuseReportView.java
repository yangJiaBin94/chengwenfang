package com.chengwenfang.mode.hotnews.facade.dto.view;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 改写后插图复用/重生报告。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HotNewsIllustrationReuseReportView {
  /** 复用槽位数。 */
  private int reusedCount;
  /** 需重生槽位数（含封面）。 */
  private int regenRequiredCount;
  /** 各槽位裁定。 */
  private List<HotNewsIllustrationSlotDecisionView> slots;
}
