package com.chengwenfang.quality.facade.dto.view;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 拒绝修复候选结果。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HotArticleRepairRejectView {
  /** 是否成功 */
  private Boolean ok;
  /** 候选 ID */
  private String candidateId;
  /** 状态 */
  private String status;
}
