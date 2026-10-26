package com.workbench.app.golden.stub;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.extension.ResponseDefinitionTransformerV2;
import com.github.tomakehurst.wiremock.http.ResponseDefinition;
import com.github.tomakehurst.wiremock.stubbing.ServeEvent;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 按特征串匹配 AI fixture 并返回固定响应；未命中任何规则时抛错，禁止静默默认响应。
 * <p>覆盖 chat、responses、images 三类 OpenAI 兼容路径。
 */
public final class ScriptedAiResponder implements ResponseDefinitionTransformerV2 {
  /** WireMock transformer 名称。 */
  public static final String NAME = "scripted-ai-responder";

  private final AiFixtureCatalog catalog;

  /** 使用 classpath catalog。 */
  public ScriptedAiResponder() {
    this(AiFixtureCatalog.loadFromClasspath());
  }

  /**
   * @param catalog 规则目录
   */
  public ScriptedAiResponder(AiFixtureCatalog catalog) {
    this.catalog = catalog;
  }

  /**
   * @return 规则目录
   */
  public AiFixtureCatalog catalog() {
    return catalog;
  }

  @Override
  public String getName() {
    return NAME;
  }

  @Override
  public boolean applyGlobally() {
    return false;
  }

  @Override
  public ResponseDefinition transform(ServeEvent serveEvent) {
    String path = serveEvent.getRequest().getUrl();
    String body = serveEvent.getRequest().getBodyAsString();
    AiFixtureRule rule = catalog.requireMatch(path, body);
    String responseBody = buildResponseBody(rule, path, body);
    return aResponse()
        .withStatus(200)
        .withHeader("Content-Type", "application/json; charset=utf-8")
        .withBody(responseBody)
        .build();
  }

  /**
   * 启动带本 Transformer 的 WireMock，并注册三类 AI 路径 stub。
   *
   * @return 已 start 的服务
   */
  public static WireMockServer startServer() {
    ScriptedAiResponder responder = new ScriptedAiResponder();
    WireMockServer server =
        new WireMockServer(wireMockConfig().dynamicPort().extensions(responder));
    server.start();
    installStubs(server);
    return server;
  }

  /**
   * 为已启动且已注册本扩展的服务器挂上 chat / responses / images stub。
   *
   * @param server WireMock
   */
  public static void installStubs(WireMockServer server) {
    server.stubFor(
        post(urlPathEqualTo("/v1/chat/completions"))
            .atPriority(1)
            .willReturn(aResponse().withTransformers(NAME)));
    server.stubFor(
        post(urlPathEqualTo("/v1/responses"))
            .atPriority(1)
            .willReturn(aResponse().withTransformers(NAME)));
    server.stubFor(
        post(urlPathEqualTo("/v1/images/generations"))
            .atPriority(1)
            .willReturn(aResponse().withTransformers(NAME)));
  }

  /**
   * 构造 OpenAI 兼容响应体。
   *
   * @param rule        命中规则
   * @param path        请求路径
   * @param requestBody 原始请求体（用于选题翻译 ID 回写）
   * @return JSON 字符串
   */
  static String buildResponseBody(AiFixtureRule rule, String path, String requestBody) {
    if ("images".equalsIgnoreCase(rule.endpoint()) || StrUtil.nullToEmpty(path).contains("/images/")) {
      Map<String, Object> dataItem = new LinkedHashMap<>();
      dataItem.put("b64_json", rule.b64Json());
      Map<String, Object> root = new LinkedHashMap<>();
      root.put("created", 1);
      root.put("data", List.of(dataItem));
      return JSONUtil.toJsonStr(root);
    }
    String text = rule.assistantText();
    String parser = rule.parser();
    if ("topic_translation".equals(parser)) {
      text = rewriteTopicTranslationIds(text, requestBody);
    } else if ("relevance".equals(parser)
        || "research_translate".equals(parser)
        || "research_item_score".equals(parser)) {
      text = rewriteRequestItemIds(text, requestBody, "items");
    }
    if (StrUtil.nullToEmpty(path).contains("/responses")) {
      return buildResponsesBody(text);
    }
    return buildChatCompletionsBody(text);
  }

  /**
   * 兼容旧签名：无请求体时不做 ID 回写。
   *
   * @param rule 命中规则
   * @param path 请求路径
   * @return JSON 字符串
   */
  static String buildResponseBody(AiFixtureRule rule, String path) {
    return buildResponseBody(rule, path, null);
  }

  /**
   * 将 fixture 中的 translations[].id 按请求 topics 顺序替换为真实选题 ID。
   *
   * @param assistantText fixture 助手正文
   * @param requestBody   chat/completions 请求体
   * @return 回写后的 JSON 文本
   */
  static String rewriteTopicTranslationIds(String assistantText, String requestBody) {
    return rewriteIdsInArray(
        assistantText, extractEditorialTopicIds(requestBody), "translations");
  }

  /**
   * 将 fixture 数组行的 id 按请求 user JSON 中 items[].id 回写并补齐。
   *
   * @param assistantText fixture 正文
   * @param requestBody   请求体
   * @param arrayKey      响应数组键（通常 items）
   * @return 回写后正文
   */
  static String rewriteRequestItemIds(
      String assistantText, String requestBody, String arrayKey) {
    return rewriteIdsInArray(assistantText, extractPayloadItemIds(requestBody), arrayKey);
  }

  /**
   * 按目标 ID 列表回写并补齐 JSON 数组中的 id 字段。
   *
   * @param assistantText fixture 正文
   * @param ids           目标 ID
   * @param arrayKey      数组键
   * @return JSON 文本
   */
  static String rewriteIdsInArray(String assistantText, List<String> ids, String arrayKey) {
    if (ids.isEmpty() || StrUtil.isBlank(assistantText) || StrUtil.isBlank(arrayKey)) {
      return StrUtil.nullToEmpty(assistantText);
    }
    JSONObject root = JSONUtil.parseObj(assistantText);
    JSONArray rows = root.getJSONArray(arrayKey);
    if (rows == null || rows.isEmpty()) {
      return assistantText;
    }
    for (int index = 0; index < rows.size() && index < ids.size(); index++) {
      JSONObject row = rows.getJSONObject(index);
      if (row != null) {
        row.set("id", ids.get(index));
      }
    }
    JSONObject template = rows.getJSONObject(0);
    while (rows.size() < ids.size() && template != null) {
      JSONObject clone = JSONUtil.parseObj(template.toString());
      clone.set("id", ids.get(rows.size()));
      rows.add(clone);
    }
    return root.toString();
  }

  /**
   * 从 chat 请求 user 消息中解析选题提炼批次的 topics[].id。
   *
   * @param requestBody 请求体
   * @return 选题 ID 列表
   */
  static List<String> extractEditorialTopicIds(String requestBody) {
    return extractUserPayloadIds(requestBody, "topics");
  }

  /**
   * 从 chat 请求 user 消息中解析 items[].id。
   *
   * @param requestBody 请求体
   * @return ID 列表
   */
  static List<String> extractPayloadItemIds(String requestBody) {
    return extractUserPayloadIds(requestBody, "items");
  }

  /**
   * 从最后一条 user 消息 JSON 中读取数组元素的 id。
   *
   * @param requestBody 请求体
   * @param arrayKey    topics / items
   * @return ID 列表
   */
  static List<String> extractUserPayloadIds(String requestBody, String arrayKey) {
    List<String> ids = new ArrayList<>();
    if (StrUtil.isBlank(requestBody)
        || StrUtil.isBlank(arrayKey)
        || !JSONUtil.isTypeJSON(requestBody)) {
      return ids;
    }
    JSONObject root = JSONUtil.parseObj(requestBody);
    JSONArray messages = root.getJSONArray("messages");
    if (messages == null) {
      return ids;
    }
    String needle = "\"" + arrayKey + "\"";
    for (int index = messages.size() - 1; index >= 0; index--) {
      JSONObject message = messages.getJSONObject(index);
      if (message == null || !"user".equals(message.getStr("role"))) {
        continue;
      }
      Object contentObj = message.get("content");
      String content;
      if (contentObj instanceof JSONObject || contentObj instanceof JSONArray) {
        content = JSONUtil.toJsonStr(contentObj);
      } else {
        content = contentObj == null ? "" : String.valueOf(contentObj);
      }
      if (StrUtil.isBlank(content) || !content.contains(needle)) {
        continue;
      }
      JSONObject payload = JSONUtil.parseObj(content);
      JSONArray rows = payload.getJSONArray(arrayKey);
      if (rows == null) {
        continue;
      }
      for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
        JSONObject row = rows.getJSONObject(rowIndex);
        if (row == null) {
          continue;
        }
        String id = row.getStr("id");
        if (StrUtil.isNotBlank(id)) {
          ids.add(id);
        }
      }
      break;
    }
    return ids;
  }

  /**
   * OpenAI chat.completions 信封。
   *
   * @param content 助手文本
   * @return JSON
   */
  public static String buildChatCompletionsBody(String content) {
    Map<String, Object> message = new LinkedHashMap<>();
    message.put("role", "assistant");
    message.put("content", StrUtil.nullToEmpty(content));
    Map<String, Object> choice = new LinkedHashMap<>();
    choice.put("index", 0);
    choice.put("message", message);
    Map<String, Object> root = new LinkedHashMap<>();
    root.put("id", "chatcmpl-golden");
    root.put("object", "chat.completion");
    root.put("choices", List.of(choice));
    return JSONUtil.toJsonStr(root);
  }

  /**
   * OpenAI responses 信封（output_text 聚合路径常用）。
   *
   * @param content 助手文本
   * @return JSON
   */
  public static String buildResponsesBody(String content) {
    Map<String, Object> outputText = new LinkedHashMap<>();
    outputText.put("type", "output_text");
    outputText.put("text", StrUtil.nullToEmpty(content));
    Map<String, Object> message = new LinkedHashMap<>();
    message.put("type", "message");
    message.put("role", "assistant");
    message.put("content", List.of(outputText));
    Map<String, Object> root = new LinkedHashMap<>();
    root.put("id", "resp-golden");
    root.put("object", "response");
    root.put("output", List.of(message));
    root.put("output_text", StrUtil.nullToEmpty(content));
    return JSONUtil.toJsonStr(root);
  }

  /**
   * 骨架占位名（兼容旧调用）。
   *
   * @return 类名
   */
  public String placeholderName() {
    return "ScriptedAiResponder";
  }
}
