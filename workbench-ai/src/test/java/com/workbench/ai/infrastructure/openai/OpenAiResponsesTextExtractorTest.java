package com.workbench.ai.infrastructure.openai;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class OpenAiResponsesTextExtractorTest {

  @Test
  void skips_reasoning_and_takes_message_output_text() {
    OpenAiResponsesResponse parsed = OpenAiResponsesResponse.builder()
        .output(List.of(
            OpenAiResponsesResponse.OutputItem.builder()
                .type("reasoning")
                .content(List.of(
                    OpenAiResponsesResponse.ContentPart.builder()
                        .type("reasoning_text")
                        .text("我们需要回答用户请求")
                        .build()))
                .build(),
            OpenAiResponsesResponse.OutputItem.builder()
                .type("message")
                .content(List.of(
                    OpenAiResponsesResponse.ContentPart.builder()
                        .type("output_text")
                        .text("# 课程标题\n\n正文")
                        .build()))
                .build()))
        .build();

    assertThat(OpenAiResponsesTextExtractor.extract(parsed))
        .isEqualTo("# 课程标题\n\n正文");
  }

  @Test
  void prefers_top_level_output_text() {
    OpenAiResponsesResponse parsed = OpenAiResponsesResponse.builder()
        .outputText("顶层正文")
        .output(List.of(
            OpenAiResponsesResponse.OutputItem.builder()
                .type("reasoning")
                .content(List.of(
                    OpenAiResponsesResponse.ContentPart.builder()
                        .type("reasoning_text")
                        .text("推理")
                        .build()))
                .build()))
        .build();

    assertThat(OpenAiResponsesTextExtractor.extract(parsed)).isEqualTo("顶层正文");
  }
}
