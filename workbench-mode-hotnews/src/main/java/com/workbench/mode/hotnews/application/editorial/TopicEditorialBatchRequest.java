package com.workbench.mode.hotnews.application.editorial;

import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 发给模型的选题批处理请求。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TopicEditorialBatchRequest {
  /** 待提炼选题。 */
  @Builder.Default
  private List<TopicEditorialSourceItem> topics = new ArrayList<>();
  /** 期望响应结构说明。 */
  private TopicEditorialResponseSchema responseSchema;

  /** 单条输入选题。 */
  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class TopicEditorialSourceItem {
    private String id;
    private String title;
    private String summary;
  }

  /** 响应 schema 提示。 */
  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class TopicEditorialResponseSchema {
    @Builder.Default
    private List<TopicEditorialTranslationHint> translations = new ArrayList<>();
  }

  /** schema 中单条字段说明。 */
  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class TopicEditorialTranslationHint {
    private String id;
    private String title;
    private String summary;
  }
}
