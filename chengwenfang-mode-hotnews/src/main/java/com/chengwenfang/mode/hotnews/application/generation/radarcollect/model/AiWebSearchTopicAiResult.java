package com.chengwenfang.mode.hotnews.application.generation.radarcollect.model;

import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * AI 联网自搜选题条目（二次结构化结果）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiWebSearchTopicAiResult {
  /** 选题标题。 */
  private String title;
  /** 出处 URL（必须来自 citations）。 */
  private String url;
  /** 内容摘要 / 事实速览。 */
  private String summary;
  /** 可写角度说明。 */
  private String whyWrite;

  /**
   * AI 联网自搜选题批次。
   */
  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class Batch {
    /** 选题列表。 */
    @Builder.Default
    private List<AiWebSearchTopicAiResult> topics = new ArrayList<>();
  }
}
