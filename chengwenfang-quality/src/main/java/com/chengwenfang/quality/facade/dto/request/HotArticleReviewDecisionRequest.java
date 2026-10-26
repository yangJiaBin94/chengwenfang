package com.chengwenfang.quality.facade.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 人工裁决请求。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HotArticleReviewDecisionRequest {
  /** 裁决状态 */
  private String status;
  /** 备注 */
  private String comment;
}
