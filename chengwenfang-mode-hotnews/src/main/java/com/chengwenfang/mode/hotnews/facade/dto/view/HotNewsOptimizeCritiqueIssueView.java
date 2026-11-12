package com.chengwenfang.mode.hotnews.facade.dto.view;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 优化诊断中的单条问题。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HotNewsOptimizeCritiqueIssueView {
  /** 维度键：narrative / tone / aiFlavor / setupCompleteness / coverage 等。 */
  private String dimension;
  /** 原文摘句证据。 */
  private String evidence;
  /** 修改建议。 */
  private String suggestion;
}
