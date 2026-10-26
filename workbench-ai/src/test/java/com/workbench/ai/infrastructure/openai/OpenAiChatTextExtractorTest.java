package com.workbench.ai.infrastructure.openai;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class OpenAiChatTextExtractorTest {

  @Test
  void extracts_content_and_ignores_reasoning_content() {
    String body = """
        {
          "choices": [{
            "message": {
              "role": "assistant",
              "content": "# 最终课程原稿\\n\\n正文。",
              "reasoning_content": "我们需要回答用户请求。先压缩再输出。"
            }
          }]
        }
        """;

    String text = OpenAiChatTextExtractor.extractFromResponseBody(body);

    assertThat(text).isEqualTo("# 最终课程原稿\n\n正文。");
    assertThat(text).doesNotContain("我们需要回答用户");
  }

  @Test
  void prefers_typed_text_blocks_like_aimessage_text() {
    String body = """
        {
          "choices": [{
            "message": {
              "content": [
                {"type": "reasoning_content", "text": "思考过程应被跳过"},
                {"type": "text", "text": "最终回答"}
              ]
            }
          }]
        }
        """;

    assertThat(OpenAiChatTextExtractor.extractFromResponseBody(body)).isEqualTo("最终回答");
  }

  @Test
  void joins_plain_string_parts() {
    Object content = List.of("一段", "二段");
    assertThat(OpenAiChatTextExtractor.extractContent(content)).isEqualTo("一段二段");
  }
}
