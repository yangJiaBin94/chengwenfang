package com.chengwenfang.ai.infrastructure.openai;

import cn.hutool.core.annotation.Alias;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** OpenAI {@code /responses} 响应体（仅解析文本所需字段）。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OpenAiResponsesResponse {
  /** 结构化输出项列表。 */
  private List<OutputItem> output;
  /** 顶层快捷文本字段（部分实现直接返回）。 */
  @Alias("output_text")
  private String outputText;

  /** 单条 output。 */
  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class OutputItem {
    /** 项类型：{@code message} / {@code reasoning} / tool 等。 */
    private String type;
    /** 内容片段列表。 */
    private List<ContentPart> content;
  }

  /** 内容片段。 */
  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class ContentPart {
    /** 片段类型，如 output_text。 */
    private String type;
    /** 文本内容。 */
    private String text;
  }
}
