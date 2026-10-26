package com.workbench.mode.hotnews.application.generation.radarcollect.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 营销判定 Skill 的固定 JSON 结构。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MarketingFilterAiResult {
  /** 是否营销内容。 */
  private Boolean marketing;
  /** 简短原因。 */
  private String reason;
}
