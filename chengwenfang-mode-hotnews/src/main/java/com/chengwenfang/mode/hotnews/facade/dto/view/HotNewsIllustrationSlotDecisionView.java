package com.chengwenfang.mode.hotnews.facade.dto.view;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 单张插图（含封面）适配裁定。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HotNewsIllustrationSlotDecisionView {
  /** 槽位序号：封面 0，正文从 1 起。 */
  private int sequenceNo;
  /** 是否封面。 */
  private boolean cover;
  /** 插图位置标记行。 */
  private String marker;
  /** reuse / regenerate。 */
  private String decision;
  /** 简短理由。 */
  private String reason;
}
