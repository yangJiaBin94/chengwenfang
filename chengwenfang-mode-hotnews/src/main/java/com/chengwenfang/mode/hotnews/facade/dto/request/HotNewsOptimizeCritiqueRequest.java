package com.chengwenfang.mode.hotnews.facade.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 热点原稿优化诊断请求。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HotNewsOptimizeCritiqueRequest {
  /** 用户补充关注点；可空。 */
  private String notes;
}
