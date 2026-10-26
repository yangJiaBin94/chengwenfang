package com.chengwenfang.app.support;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;

import com.github.tomakehurst.wiremock.WireMockServer;
import java.util.Map;

/** Shared WireMock stubs for OpenAI-compatible text/image used by formal generation ITs. */
public final class FormalAiWireMock {
  private FormalAiWireMock() {}

  public static WireMockServer start() {
    WireMockServer server = new WireMockServer(wireMockConfig().dynamicPort());
    server.start();
    return server;
  }

  public static void stubDefaults(WireMockServer server) {
    server.resetAll();
    String longPad = "围绕本课主题展开讲解，给出可执行步骤与边界说明，帮助读者完成最小实践。".repeat(8);
    stubChat(server, "开场", """
        ## 开场与本课目标
        从具体问题切入。%s
        """.formatted(longPad));
    stubChat(server, "核心", """
        ## 核心原理
        解释关键概念与边界。%s
        """.formatted(longPad));
    stubChat(server, "案例", """
        ## 案例与推导
        通过例子巩固。%s

        ## 插图位置：结构关系

        ```text
        concept structure diagram for the lesson
        ```
        """.formatted(longPad));
    stubChat(server, "小结", """
        ## 本课小结
        - 理解本节主题
        - 完成一次最小实践
        %s
        """.formatted(longPad));
    stubChat(server, "预告", """
        ## 下节课预告
        - 下一课将继续扩展应用场景
        %s
        """.formatted(longPad));
    stubChat(server, "压缩", """
        # 压缩稿
        %s
        ## 本课小结
        - ok
        ## 下节课预告
        - next
        """.formatted(longPad));
    // wechat chunks / generic
    server.stubFor(post(urlPathEqualTo("/v1/chat/completions"))
        .atPriority(10)
        .willReturn(aResponse()
            .withHeader("Content-Type", "application/json")
            .withBody(chatBody("""
                转换后的公众号段落。%s

                [[COURSE_IMAGE_1]]

                ## 本课小结
                - 理解本节主题
                - 完成一次最小实践

                ## 下节课预告
                - 下一课将继续扩展应用场景
                """.formatted(longPad)))));

    String tinyPng = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==";
    server.stubFor(post(urlPathEqualTo("/v1/images/generations"))
        .willReturn(aResponse()
            .withHeader("Content-Type", "application/json")
            .withBody("""
                {"created":1,"data":[{"b64_json":"%s"}]}
                """.formatted(tinyPng))));
  }

  public static void pointProviders(SettingsPutter putter, WireMockServer server) throws Exception {
    String base = server.baseUrl() + "/v1";
    putter.put(Map.of(
        "text", Map.of(
            "baseUrl", base,
            "apiKey", "test-key",
            "model", "gpt-test",
            "protocol", "openai_chat"),
        "image", Map.of(
            "baseUrl", base,
            "apiKey", "test-key",
            "model", "gpt-image-test",
            "protocol", "openai_images"),
        "vision", Map.of(
            "baseUrl", base,
            "apiKey", "test-key",
            "model", "gpt-vision-test",
            "protocol", "openai_chat")));
  }

  @FunctionalInterface
  public interface SettingsPutter {
    Map<String, Object> put(Object body) throws Exception;
  }

  private static void stubChat(WireMockServer server, String needle, String content) {
    server.stubFor(post(urlPathEqualTo("/v1/chat/completions"))
        .atPriority(1)
        .withRequestBody(containing(needle))
        .willReturn(aResponse()
            .withHeader("Content-Type", "application/json")
            .withBody(chatBody(content))));
  }

  private static String chatBody(String content) {
    String escaped = content
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n");
    return """
        {"id":"chatcmpl-test","choices":[{"message":{"role":"assistant","content":"%s"}}]}
        """.formatted(escaped);
  }
}
