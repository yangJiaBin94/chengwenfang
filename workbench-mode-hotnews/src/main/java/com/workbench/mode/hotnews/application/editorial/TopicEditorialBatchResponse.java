package com.workbench.mode.hotnews.application.editorial;

import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 模型返回的选题中文提炼结果。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TopicEditorialBatchResponse {
  /** 翻译/提炼条目。 */
  @Builder.Default
  private List<TopicEditorialTranslation> translations = new ArrayList<>();

  /** 单条提炼结果。 */
  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class TopicEditorialTranslation {
    private String id;
    private String title;
    private String summary;
  }
}
