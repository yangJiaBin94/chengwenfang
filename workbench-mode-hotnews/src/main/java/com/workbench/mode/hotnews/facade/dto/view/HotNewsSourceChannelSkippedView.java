package com.workbench.mode.hotnews.facade.dto.view;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * AI 生成来源渠道时跳过的候选摘要。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HotNewsSourceChannelSkippedView {
  /** 候选名称。 */
  private String name;
  /** 渠道类型。 */
  private String type;
  /** 入口地址。 */
  private String baseUrl;
  /** 跳过原因（中文）。 */
  private String reason;
}
