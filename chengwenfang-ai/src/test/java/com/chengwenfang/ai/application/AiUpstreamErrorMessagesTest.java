package com.chengwenfang.ai.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** {@link AiUpstreamErrorMessages} 可读化用例。 */
class AiUpstreamErrorMessagesTest {

  @Test
  void humanize_htmlRequestBlocked_returnsShortChinese() {
    String raw =
        """
        文本补全失败：<!DOCTYPE html>
        <html lang="en"><head><title>Error - Request Blocked</title></head>
        <body><h1>Request Blocked</h1>
        <a href="https://www.deepseek.com/">Visit the Official DeepSeek Website</a>
        </body></html>
        """;
    String message = AiUpstreamErrorMessages.humanize(raw);
    assertThat(message).doesNotContain("<!DOCTYPE", "<html", "<body");
    assertThat(message).contains("拦截");
  }

  @Test
  void humanize_htmlWithoutBlockedHint_mentionsWrongUrl() {
    String raw = "<!DOCTYPE html><html><body><h1>Not Found</h1></body></html>";
    String message = AiUpstreamErrorMessages.humanize(raw);
    assertThat(message).contains("请求地址");
    assertThat(message).doesNotContain("<html");
  }

  @Test
  void humanize_openaiJsonError_extractsMessage() {
    String raw = "{\"error\":{\"message\":\"Model not found\",\"type\":\"invalid_request_error\"}}";
    assertThat(AiUpstreamErrorMessages.humanize(raw)).isEqualTo("Model not found");
  }

  @Test
  void humanize_shortChinese_passthrough() {
    assertThat(AiUpstreamErrorMessages.humanize("未配置文本模型 API Key"))
        .isEqualTo("未配置文本模型 API Key");
  }
}
