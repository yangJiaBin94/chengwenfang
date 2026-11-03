package com.chengwenfang.mode.hotnews.application.research;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Agent finish 动作中选用的一条素材（URL 必须来自工具命中）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResearchAgentSelectedItem {
  /** 素材 URL。 */
  private String url;
  /** 标题。 */
  private String title;
  /** 选用理由。 */
  private String reason;
}
