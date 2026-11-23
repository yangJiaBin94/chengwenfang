package com.chengwenfang.mode.hotnews.application.research;

import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 资料补充 AI 联网搜结果条目（二次结构化）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResearchAiWebSearchItem {
  /** 资料标题。 */
  private String title;
  /** 出处 URL（必须来自 citations）。 */
  private String url;
  /** 事实摘要 / 为何可作为佐证。 */
  private String summary;

  /**
   * AI 联网搜资料批次。
   */
  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class Batch {
    /** 资料列表。 */
    @Builder.Default
    private List<ResearchAiWebSearchItem> items = new ArrayList<>();
  }
}
