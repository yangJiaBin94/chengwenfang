package com.workbench.quality.facade.dto.view;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 人工裁决结果。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HotArticleReviewDecisionView {
  /** 是否成功 */
  private Boolean ok;
  /** 审稿 ID */
  private String reviewId;
  /** 裁决状态 */
  private String status;
}
