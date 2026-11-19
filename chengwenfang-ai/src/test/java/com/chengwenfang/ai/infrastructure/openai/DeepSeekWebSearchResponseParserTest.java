package com.chengwenfang.ai.infrastructure.openai;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** {@link DeepSeekWebSearchResponseParser} 引用与正文提取。 */
class DeepSeekWebSearchResponseParserTest {

  @Test
  void parseResponses_readsOutputTextAndCitations() {
    String body = """
        {
          "output_text": "热点摘要",
          "output": [
            {
              "type": "message",
              "content": [
                {
                  "type": "output_text",
                  "text": "热点摘要",
                  "annotations": [
                    {"type": "url_citation", "url": "https://example.com/a", "title": "A"},
                    {"url_citation": {"url": "https://example.com/b", "title": "B"}}
                  ]
                }
              ]
            }
          ]
        }
        """;
    var parsed = DeepSeekWebSearchResponseParser.parseResponses(body);
    assertThat(parsed.text()).contains("热点摘要");
    assertThat(parsed.citations()).extracting(c -> c.getUrl())
        .containsExactly("https://example.com/a", "https://example.com/b");
  }

  @Test
  void parseChat_readsMessageContentAndCitations() {
    String body = """
        {
          "choices": [
            {
              "message": {
                "content": "回答正文",
                "citations": [
                  {"url": "https://news.example/x", "title": "X"},
                  {"url": "javascript:alert(1)", "title": "bad"}
                ]
              }
            }
          ]
        }
        """;
    var parsed = DeepSeekWebSearchResponseParser.parseChat(body);
    assertThat(parsed.text()).isEqualTo("回答正文");
    assertThat(parsed.citations()).hasSize(1);
    assertThat(parsed.citations().get(0).getUrl()).isEqualTo("https://news.example/x");
  }
}
