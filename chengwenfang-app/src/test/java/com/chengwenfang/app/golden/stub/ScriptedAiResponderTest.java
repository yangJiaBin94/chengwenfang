package com.chengwenfang.app.golden.stub;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.github.tomakehurst.wiremock.WireMockServer;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * {@link ScriptedAiResponder}：优先级匹配、未命中失败、三类端点信封。
 */
class ScriptedAiResponderTest {
  private WireMockServer server;

  @BeforeEach
  void start() {
    server = ScriptedAiResponder.startServer();
  }

  @AfterEach
  void stop() {
    if (server != null) {
      server.stop();
    }
  }

  @Test
  void chat_matches_keyword_fixture() {
    String body =
        """
        {"messages":[{"role":"system","content":"你是热点选题资料检索助手。disambiguation：说明"},
        {"role":"user","content":"选题"}]}
        """;
    try (HttpResponse response =
        HttpRequest.post(server.baseUrl() + "/v1/chat/completions")
            .body(body)
            .contentType("application/json")
            .execute()) {
      assertThat(response.getStatus()).isEqualTo(200);
      JSONObject root = JSONUtil.parseObj(response.body());
      String content = root.getByPath("choices[0].message.content", String.class);
      assertThat(content).contains("Apple Inc");
    }
  }

  @Test
  void higher_priority_wins_over_broader_needle() {
    // recommendUse 同时出现在 writing_score；disambiguation 只命中 keywords（priority 20 < 30）
    String body =
        """
        {"messages":[{"role":"system","content":"热点选题资料检索助手 disambiguation"},
        {"role":"user","content":"x"}]}
        """;
    try (HttpResponse response =
        HttpRequest.post(server.baseUrl() + "/v1/chat/completions")
            .body(body)
            .contentType("application/json")
            .execute()) {
      String content =
          JSONUtil.parseObj(response.body()).getByPath("choices[0].message.content", String.class);
      assertThat(content).contains("\"subject\"");
    }
  }

  @Test
  void images_endpoint_returns_b64() {
    try (HttpResponse response =
        HttpRequest.post(server.baseUrl() + "/v1/images/generations")
            .body("{\"prompt\":\"x\"}")
            .contentType("application/json")
            .execute()) {
      assertThat(response.getStatus()).isEqualTo(200);
      String b64 =
          JSONUtil.parseObj(response.body()).getByPath("data[0].b64_json", String.class);
      assertThat(b64).startsWith("iVBOR");
    }
  }

  @Test
  void responses_endpoint_uses_responses_envelope() {
    String body = "{\"input\":\"RESPONSES_PROTOCOL_PROBE\"}";
    try (HttpResponse response =
        HttpRequest.post(server.baseUrl() + "/v1/responses")
            .body(body)
            .contentType("application/json")
            .execute()) {
      assertThat(response.getStatus()).isEqualTo(200);
      JSONObject root = JSONUtil.parseObj(response.body());
      assertThat(root.getStr("output_text")).isEqualTo("responses-ok");
    }
  }

  @Test
  void unmatched_request_fails() {
    // Transformer 抛错时 WireMock 返回 5xx，同时 requireMatch 单测覆盖显式失败文案
    try (HttpResponse response =
        HttpRequest.post(server.baseUrl() + "/v1/chat/completions")
            .body("{\"messages\":[{\"role\":\"user\",\"content\":\"NO_MATCH_NEEDLE_XYZ\"}]}")
            .contentType("application/json")
            .execute()) {
      assertThat(response.getStatus()).isGreaterThanOrEqualTo(500);
    }
  }

  @Test
  void catalog_requireMatch_throws_when_miss() {
    AiFixtureCatalog catalog = AiFixtureCatalog.loadFromClasspath();
    assertThatThrownBy(() -> catalog.requireMatch("/v1/chat/completions", "nothing-here"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("未命中");
  }

  @Test
  void topic_translation_rewrites_ids_from_request() {
    String userPayload =
        "{\"topics\":[{\"id\":\"topic-a\",\"title\":\"Sleep\"},{\"id\":\"topic-b\",\"title\":\"Melatonin\"}],"
            + "\"responseSchema\":{\"translations\":[{\"id\":\"原 id\",\"title\":\"简体中文标题\"}]}}";
    JSONObject request = new JSONObject();
    request.set(
        "messages",
        java.util.List.of(
            Map.of("role", "system", "content", "你是中文 AI 科技资讯编辑。"),
            Map.of("role", "user", "content", userPayload)));
    try (HttpResponse response =
        HttpRequest.post(server.baseUrl() + "/v1/chat/completions")
            .body(request.toString())
            .contentType("application/json")
            .execute()) {
      assertThat(response.getStatus()).isEqualTo(200);
      String content =
          JSONUtil.parseObj(response.body()).getByPath("choices[0].message.content", String.class);
      assertThat(content).as("assistant content").isNotBlank();
      JSONObject root = JSONUtil.parseObj(content);
      assertThat(root.getJSONArray("translations")).as("translations: %s", content).isNotEmpty();
      assertThat(root.getJSONArray("translations").getJSONObject(0).getStr("id"))
          .as("content=%s", content)
          .isEqualTo("topic-a");
      assertThat(root.getJSONArray("translations").getJSONObject(1).getStr("id"))
          .isEqualTo("topic-b");
      assertThat(root.getJSONArray("translations").getJSONObject(0).getStr("title")).contains("睡眠");
    }
  }

  @Test
  void rewriteTopicTranslationIds_unit() {
    String user = "{\"topics\":[{\"id\":\"topic-a\"},{\"id\":\"topic-b\"}]}";
    String body =
        "{\"messages\":[{\"role\":\"user\",\"content\":" + JSONUtil.quote(user) + "}]}";
    assertThat(ScriptedAiResponder.extractEditorialTopicIds(body))
        .containsExactly("topic-a", "topic-b");
    String rewritten =
        ScriptedAiResponder.rewriteTopicTranslationIds(
            "{\"translations\":[{\"id\":\"t1\",\"title\":\"睡眠\",\"summary\":\"速览\"}]}", body);
    assertThat(JSONUtil.parseObj(rewritten).getJSONArray("translations").getJSONObject(0).getStr("id"))
        .isEqualTo("topic-a");
    assertThat(JSONUtil.parseObj(rewritten).getJSONArray("translations").getJSONObject(1).getStr("id"))
        .isEqualTo("topic-b");
  }
}
